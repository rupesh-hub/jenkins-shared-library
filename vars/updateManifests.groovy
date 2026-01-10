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
        
        // Use single quotes for the shell script to avoid Groovy interpolation warnings
        sh '''
            git config user.name "jenkins-cd-bot"
            git config user.email "jenkins-cd@alfarays.com"
            
            # Use single quotes for the URL and environment variables to satisfy Jenkins security
            git remote set-url origin "https://${GIT_USER}:${GIT_TOKEN}@github.com/rupesh-hub/FitVerse-2026-01-01.git"

            # --- Update YAML Files ---
            find docker/docker-compose -name "docker-compose.yaml" | xargs -I {} \
                yq -i '.services."'"${fullServiceName}"'".image = "'"${imageName}"'"' {}

            if [ -f "''' + k8sFilePath + '''" ]; then
                yq -i '(.spec.template.spec.containers[] | select(.name == "'"${fullServiceName}"'") | .image) = "'"${imageName}"'"' ''' + k8sFilePath + '''
            fi

            if [ -f "helm/values.yaml" ]; then
                yq -i ".''' + serviceName + '''.image.tag = \\"''' + version + '''\\"" helm/values.yaml
            fi

            # --- Commit and Push ---
            git add .
            if git diff --staged --quiet; then
                echo "No changes to commit."
            else
                git commit -m "chore(''' + serviceName + '''): deploy version ''' + version + ''' [skip ci]"
                
                # Fetch first to ensure we are up to date
                git fetch origin main
                
                # PUSH FIX: Push the local HEAD to the remote main branch
                git push origin HEAD:main
            fi
        '''
    }
}