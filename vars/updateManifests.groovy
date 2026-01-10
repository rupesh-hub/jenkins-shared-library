def call(Map config = [:]) {
    // Dynamic parameters
    def serviceName      = config.serviceName 
    def version          = config.version
    def dockerUser       = config.dockerUser ?: 'rupesh1997'
    def gitCredentialsId = config.gitCredentialsId ?: 'git-creds'

    // Derived variables
    def fullServiceName  = "fitverse-${serviceName}"
    def imageName        = "${dockerUser}/${fullServiceName}:${version}"
    def k8sFilePath      = "kubernetes/${serviceName}-deployment.yaml"

    echo "--- Dynamic CD Update: ${fullServiceName} to version ${version} ---"

    // Replacing sshagent with withCredentials
    withCredentials([sshUserPrivateKey(credentialsId: gitCredentialsId, keyFileVariable: 'SSH_KEY')]) {
        
        // 1. Setup Git Identity & SSH Command
        // StrictHostKeyChecking=no avoids the "Host key verification failed" error
        sh """
            git config user.name "jenkins-cd-bot"
            git config user.email "jenkins-cd@alfarays.com"
            chmod 600 ${SSH_KEY}
            export GIT_SSH_COMMAND="ssh -i ${SSH_KEY} -o StrictHostKeyChecking=no"
        """

        // 2. Dynamic YAML Updates using yq
        sh """
            # Update Docker Compose files
            find docker/docker-compose -name "docker-compose.yaml" | xargs -I {} \
                yq -i '.services."${fullServiceName}".image = "${imageName}"' {}

            # Update Kubernetes Deployment
            if [ -f "${k8sFilePath}" ]; then
                yq -i '(.spec.template.spec.containers[] | select(.name == "${fullServiceName}") | .image) = "${imageName}"' ${k8sFilePath}
            fi

            # Update Helm Values
            if [ -f "helm/values.yaml" ]; then
                yq -i '.${serviceName}.image.tag = "${version}"' helm/values.yaml
            fi
        """

        // 3. Robust Git Push
        sh """
            export GIT_SSH_COMMAND="ssh -i ${SSH_KEY} -o StrictHostKeyChecking=no"
            git add .
            if git diff --staged --quiet; then
                echo "No changes detected for ${fullServiceName}."
            else
                git commit -m "chore(${serviceName}): deploy version ${version} [skip ci]"
                git pull --rebase origin main
                git push origin main
            fi
        """
    }
}