pipeline {
    agent {
        label "jenkins-agent"
    }
    tools {
        jdk 'Java21'
        maven 'Maven3'
    }
    environment {
        APP_NAME = "startup-service"
        RELEASE = "1.0.0"
        DOCKERHUB_USER = "mikiyaslemma"
        SERVER_USERNAME = "ubuntu"
        SERVER_IP = "172.20.136.101"
        IMAGE_NAME = "${DOCKERHUB_USER}/${APP_NAME}"
        IMAGE_TAG = "${RELEASE}-${BUILD_NUMBER}"
    }
    stages {
        stage('Cleanup Workspace') {
            steps {
                echo "Cleaning up the workspace..."
                cleanWs()
            }
        }

        stage('Checkout SCM') {
            steps {
                echo "Checking out ${APP_NAME} module from Git repository..."
                git branch: 'main', credentialsId: 'github', url: 'https://github.com/dpi-saas/saas-backend'
            }
        }

        stage('Build Module') {
            steps {
                echo "Building the ${APP_NAME} module..."
                dir("${APP_NAME}") {
                    sh "mvn clean package -DskipTests"
                }
            }
        }

        stage('Test Module') {
            steps {
                echo "Running tests for ${APP_NAME}..."
                dir("${APP_NAME}") {
                    sh "mvn test"
                }
            }
        }

        stage('SonarQube Analysis') {
            steps {
                script {
                    echo "Performing SonarQube analysis on ${APP_NAME}..."
                    withSonarQubeEnv('sonarqube-server') {
                        dir("${APP_NAME}") {
                            sh "mvn sonar:sonar"
                        }
                    }
                }
            }
        }

        stage('Build & Push Docker Image') {
            steps {
                dir("${APP_NAME}") {
                    echo "Building and pushing Docker images (tag: ${IMAGE_TAG}" +
                         " and latest) for ${APP_NAME} using Jib..."

                    withCredentials([usernamePassword(credentialsId: 'dockerhub',
                                    usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKERHUB_TOKEN')]) {
                        sh """
                        mvn clean compile jib:build \
                            -Dimage=${IMAGE_NAME}:${IMAGE_TAG} \
                            -Djib.to.auth.username=${DOCKERHUB_USER} \
                            -Djib.to.auth.password=${DOCKERHUB_TOKEN}
                        """

                        sh """
                            mvn compile jib:build \
                                -Dimage=${IMAGE_NAME}:latest \
                                -Djib.to.auth.username=${DOCKERHUB_USER} \
                                -Djib.to.auth.password=${DOCKERHUB_TOKEN}
                        """
                    }
                }
            }
        }

        stage('Trivy Scan') {
            steps {
                script {
                    echo "Performing Trivy vulnerability scan on the Docker image..."
                    sh """
                    docker run -v /var/run/docker.sock:/var/run/docker.sock \
                        aquasec/trivy image ${IMAGE_NAME}:latest \
                        --no-progress --scanners vuln \
                        --exit-code 0 --severity HIGH,CRITICAL \
                        --format table > trivy-scan-results-${APP_NAME}.txt
                    """

                    // Archive the results in Jenkins
                    archiveArtifacts artifacts: "trivy-scan-results-${APP_NAME}.txt", allowEmptyArchive: true

                    // Display results in the console
                    echo "Trivy Scan Results:"
                    sh "cat trivy-scan-results-${APP_NAME}.txt"
                }
            }
        }

        stage('Deploy to Kubernetes') {
            steps {
                echo "Testing SSH connection to ${SERVER_USERNAME}@${SERVER_IP}..."
                withCredentials([
                  sshUserPrivateKey(credentialsId: 'jenkins-agent-key', keyFileVariable: 'SSH_KEY'),
                  usernamePassword(credentialsId: 'dockerhub', usernameVariable: 'DOCKER_USER',
                   passwordVariable: 'DOCKERHUB_TOKEN')]) {
                    sh '''
                        ssh -i ${SSH_KEY} -o StrictHostKeyChecking=no ${SERVER_USERNAME}@${SERVER_IP} \
                         "echo 'Logged in to server successfully.'"

                        echo "Creating deployment directory on server..."
                        ssh -i ${SSH_KEY} ${SERVER_USERNAME}@${SERVER_IP} "mkdir -p saas-backend/deployment"

                        echo "Copying Kubernetes manifests..."
                        scp -i ${SSH_KEY} -r deployment/k8s ${SERVER_USERNAME}@${SERVER_IP}:saas-backend/deployment

                        echo "Pulling latest Docker image..."
                        ssh -i ${SSH_KEY} ${SERVER_USERNAME}@${SERVER_IP} \
                         "docker login -u ${DOCKER_USER} -p ${DOCKERHUB_TOKEN} && \
                          docker pull ${DOCKERHUB_USER}/${APP_NAME}:latest"

                        echo "Applying Kubernetes manifests..."
                        ssh -i ${SSH_KEY} ${SERVER_USERNAME}@${SERVER_IP} \
                         "kubectl apply -f saas-backend/deployment/k8s/apps/${APP_NAME}.yaml"

                        echo "Restarting Kubernetes deployment..."
                        ssh -i ${SSH_KEY} ${SERVER_USERNAME}@${SERVER_IP} "
                            kubectl rollout restart deployment ${APP_NAME}
                            kubectl rollout status deployment ${APP_NAME}
                        "
                    '''
                }
            }
        }
    }

    post {
        success {
            echo "Pipeline for ${APP_NAME} completed successfully."
        }
        failure {
            echo "Pipeline for ${APP_NAME} failed. Please check the logs."
        }
        always {
            echo "Final cleanup after ${APP_NAME} pipeline..."
            cleanWs()
        }
    }
}