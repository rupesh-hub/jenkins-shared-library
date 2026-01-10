def call(Map config = [:]) {
    def serviceName      = config.serviceName 
    def version          = config.version
    def dockerUser       = config.dockerUser ?: 'rupesh1997'
    def gitCredentialsId = config.gitCredentialsId ?: 'git-ssh-cred' // This is your 'Username with password' ID

    def fullServiceName  = "fitverse-${serviceName}"
    def imageName        = "${dockerUser}/${fullServiceName}:${version}"
    def k8sFilePath      = "kubernetes/${serviceName}-deployment.yaml"

    echo "--- Dynamic CD Update: ${fullServiceName} using HTTPS Auth ---"

    // Use usernamePassword instead of sshUserPrivateKey
    withCredentials([usernamePassword(credentialsId: gitCredentialsId, usernameVariable: 'GIT_USER', passwordVariable: 'GIT_TOKEN')]) {
        
        // 1. Git Setup
        sh """
            git config user.name "jenkins-cd-bot"
            git config user.email "jenkins-cd@alfarays.com"
            
            # Reconfigure the remote to include the token for authentication
            # This avoids the "Username for 'https://github.com':" prompt
            git remote set-url origin https://${GIT_USER}:${GIT_TOKEN}@github.com/rupesh-hub/FitVerse-2026-01-01.git
        """

        // 2. Dynamic YAML Updates using yq
        sh """
            # Update Docker Compose
            find docker/docker-compose -name "docker-compose.yaml" | xargs -I {} \
                yq -i '.services."${fullServiceName}".image = "${imageName}"' {}

            # Update K8s
            if [ -f "${k8sFilePath}" ]; then
                yq -i '(.spec.template.spec.containers[] | select(.name == "${fullServiceName}") | .image) = "${imageName}"' ${k8sFilePath}
            fi

            # Update Helm
            if [ -f "helm/values.yaml" ]; then
                yq -i '.${serviceName}.image.tag = "${version}"' helm/values.yaml
            fi
        """

        // 3. Push over HTTPS
        sh """
            git add .
            if git diff --staged --quiet; then
                echo "No changes detected."
            else
                git commit -m "chore(${serviceName}): deploy version ${version} [skip ci]"
                git pull --rebase origin main
                git push origin main
            fi
        """
    }
}