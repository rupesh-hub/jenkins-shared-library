def call(Map config = [:]) {
    def serviceName      = config.serviceName 
    def version          = config.version
    def dockerUser       = config.dockerUser ?: 'rupesh1997'
    def gitCredentialsId = config.gitCredentialsId ?: 'git-ssh-cred'

    def fullServiceName  = "fitverse-${serviceName}"
    def imageName        = "${dockerUser}/${fullServiceName}:${version}"
    def k8sFilePath      = "kubernetes/${serviceName}-deployment.yaml"

    echo "--- Updating Manifests for ${fullServiceName} ---"

    withCredentials([usernamePassword(credentialsId: gitCredentialsId, usernameVariable: 'GIT_USER', passwordVariable: 'GIT_TOKEN')]) {
        
        sh '''
            git config user.name "jenkins-cd-bot"
            git config user.email "jenkins-cd@alfarays.com"
            git remote set-url origin "https://${GIT_USER}:${GIT_TOKEN}@github.com/rupesh-hub/FitVerse-2026-01-01.git"

            # --- Update YAML Files ---
            
            # Use 'select' to only update if the service exists. This prevents creating the '""' key.
            # We also target ONLY the production folder to be safe.
            find docker/docker-compose/production -name "docker-compose.yaml" | xargs -I {} \
                yq -i 'with(.services."''' + fullServiceName + '''"; select(.) .image = "''' + imageName + '''")' {}

            # Kubernetes update
            if [ -f "''' + k8sFilePath + '''" ]; then
                yq -i '(.spec.template.spec.containers[] | select(.name == "''' + fullServiceName + '''") | .image) = "''' + imageName + '''"' ''' + k8sFilePath + '''
            fi

            # Helm update
            if [ -f "helm/values.yaml" ]; then
                yq -i ".''' + serviceName + '''.image.tag = \\"''' + version + '''\\"" helm/values.yaml
            fi

            # --- Commit and Push ---
            git add .
            if git diff --staged --quiet; then
                echo "No changes to commit."
            else
                git commit -m "chore(''' + serviceName + '''): deploy version ''' + version + ''' [skip ci]"
                git fetch origin main
                git push origin HEAD:main
            fi
        '''
    }
}