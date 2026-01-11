def call(Map config = [:]) {
    def gitCredentialsId = config.gitCredentialsId ?: 'git-ssh-cred'
    // services is a list of maps: [[dockerName: 'fitverse-backend', helmName: 'backend', tag: '1.0.1']]
    def services = config.services ?: []

    echo "--- Updating Manifests for ${services.size()} services ---"

    withCredentials([usernamePassword(credentialsId: gitCredentialsId, usernameVariable: 'GIT_USER', passwordVariable: 'GIT_TOKEN')]) {
        sh """
            git config user.name "jenkins-cd-bot"
            git config user.email "jenkins-cd@alfarays.com"
            
            ORIGIN_URL=\$(git config --get remote.origin.url | sed 's|https://||')
            git remote set-url origin "https://${GIT_USER}:${GIT_TOKEN}@\$ORIGIN_URL"

            ${services.collect { svc -> 
                """
                echo "Processing ${svc.dockerName} (Helm: ${svc.helmName}) -> Tag: ${svc.tag}"

                # 1. Update ALL docker-compose files (using dockerName)
                find . -name "docker-compose*.yaml" -o -name "docker-compose*.yml" | xargs -I {} sh -c '
                    if yq ".services.\\"${svc.dockerName}\\"" {} | grep -qv "null"; then
                        yq -i ".services.\\"${svc.dockerName}\\".image |= sub(\\":.*\\", \\":${svc.tag}\\")" {}
                    fi
                '

                # 2. Update Helm values (using helmName)
                if [ -f "helm/values.yaml" ]; then
                    if yq ".${svc.helmName}" helm/values.yaml | grep -qv "null"; then
                         yq -i ".${svc.helmName}.image.tag = \\"${svc.tag}\\"" helm/values.yaml
                    fi
                fi
                """
            }.join('\n')}

            git add .
            if git diff --staged --quiet; then
                echo "No changes to commit."
            else
                git commit -m "chore(ops): deployment update for ${services.collect{it.helmName}.join(', ')} [skip ci]"
                git push origin HEAD:main
            fi
        """
    }
}