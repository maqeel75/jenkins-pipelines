import hudson.model.Node.Mode
import hudson.slaves.*
import jenkins.model.Jenkins
import hudson.plugins.sshslaves.SSHLauncher

library changelog: false, identifier: 'lib@master', retriever: modernSCM([
    $class: 'GitSCMSource',
    remote: 'https://github.com/Percona-Lab/jenkins-pipelines.git'
]) _

pipeline {
    agent {
        label 'awscli'
    }
    
    parameters {
        string(
            defaultValue: '',
            description: 'public ssh key for "ec2-user" user for accessing portal',
            name: 'SSH_KEY')
        string(
            defaultValue: 'main',
            description: 'Tag/Branch for infra repository',
            name: 'GIT_BRANCH')
        string(
            defaultValue: 'latest',
            description: 'Docker tag for authed service',
            name: 'AUTHED_TAG')
        string(
            defaultValue: 'latest',
            description: 'Docker tag for orgd service',
            name: 'ORGD_TAG')
        string(
            defaultValue: 'latest',
            description: 'Docker tag for telemetryd service',
            name: 'TELEMETRYD_TAG')
        string(
            defaultValue: 'latest',
            description: 'Docker tag for checked service',
            name: 'CHECKED_TAG')
        string(
            defaultValue: 'latest',
            description: 'Docker tag for saas-ui service',
            name: 'SAAS_UI_TAG')
        string(
            defaultValue: '1',
            description: 'Stop the instance after, days ("0" value disables autostop and recreates instance in case of AWS failure)',
            name: 'DAYS')
        choice(
            name: 'NOTIFY',
            choices: ['PM', 'channel', 'disable'],
            description: '')
    }

    environment {
        OKTA_TOKEN=credentials('OKTA_TOKEN');
        OAUTH_CLIENT_ID=credentials('OAUTH_CLIENT_ID');
        OAUTH_CLIENT_SECRET=credentials('OAUTH_CLIENT_SECRET');
        OAUTH_PMM_CLIENT_ID=credentials('OAUTH_PMM_CLIENT_ID');
        OAUTH_PMM_CLIENT_SECRET=credentials('OAUTH_PMM_CLIENT_SECRET');
        DOCKER_REGISTRY_PASSWORD=credentials('DOCKER_REGISTRY_PASSWORD');
        ORGD_SES_KEY=credentials('ORGD_SES_KEY');
        ORGD_SES_SECRET=credentials('ORGD_SES_SECRET');
        ORGD_SERVICENOW_PASSWORD=credentials('ORGD_SERVICENOW_PASSWORD');
        OAUTH_ISSUER_URL="https://id-dev.percona.com/oauth2/aus15pi5rjdtfrcH51d7";
        DOCKER_REGISTRY_USERNAME="percona-robot";
        OKTA_URL_DEV="id-dev.percona.com";
        OAUTH_SCOPES="percona";
        MINIKUBE_MEM=16384;
        MINIKUBE_CPU=8;
    }

    stages {
        stage('Prepare') {
            steps {
                deleteDir()
                wrap([$class: 'BuildUser']) {
                    sh """
                        echo "\${BUILD_USER_EMAIL}" > OWNER_EMAIL
                        echo "\${BUILD_USER_EMAIL}" | awk -F '@' '{print \$1}' > OWNER_FULL
                        echo "portal-\$(cat OWNER_FULL)-\$(date -u '+%Y%m%d%H%M%S')-${BUILD_NUMBER}" \
                            > VM_NAME
                    """
                }
                script {
                    def OWNER = sh(returnStdout: true, script: "cat OWNER_FULL").trim()
                    def OWNER_EMAIL = sh(returnStdout: true, script: "cat OWNER_EMAIL").trim()
                    def OWNER_SLACK = slackUserIdFromEmail(botUser: true, email: "${OWNER_EMAIL}", tokenCredentialId: 'JenkinsCI-SlackBot-v2')
                }
            }
        }

        stage('Run VM') {
            steps {
                launchSpotInstance('m5.2xlarge', '0.43', 20)
                withCredentials([sshUserPrivateKey(credentialsId: 'aws-jenkins', keyFileVariable: 'KEY_PATH', passphraseVariable: '', usernameVariable: 'USER')]) {
                    sh """
                        until ssh -i "${KEY_PATH}" -o ConnectTimeout=1 -o StrictHostKeyChecking=no ${USER}@\$(cat IP) 'java -version; sudo yum install -y java-1.8.0-openjdk; sudo /usr/sbin/alternatives --set java /usr/lib/jvm/jre-1.8.0-openjdk.x86_64/bin/java; java -version;' ; do
                            sleep 5
                        done

                        pwd
                    """
                }
                script {
                    env.IP      = sh(returnStdout: true, script: "cat IP").trim()
                    env.VM_NAME = sh(returnStdout: true, script: "cat VM_NAME").trim()

                    SSHLauncher ssh_connection = new SSHLauncher(env.IP, 22, 'aws-jenkins')
                    DumbSlave node = new DumbSlave(env.VM_NAME, "spot instance job", "/home/ec2-user/", "1", Mode.EXCLUSIVE, "", ssh_connection, RetentionStrategy.INSTANCE)

                    Jenkins.instance.addNode(node)
                }
                node(env.VM_NAME){
                    sh """
                        set -o errexit
                        set -o xtrace

                        if [ -n "$SSH_KEY" ]; then
                            echo '$SSH_KEY' >> /home/ec2-user/.ssh/authorized_keys
                        fi

                        sudo yum -y update --security
                        sudo yum -y install git svn docker
                        sudo amazon-linux-extras install epel -y
                        sudo usermod -aG docker ec2-user
                        sudo systemctl start docker

                        # Install golang, conntrack, nss-tools and minisign
                        sudo yum install golang conntrack nss-tools minisign -y

                        # Install mkcert
                        curl -sSL https://github.com/FiloSottile/mkcert/releases/download/v1.4.1/mkcert-v1.4.1-linux-amd64 > mkcert && chmod +x mkcert
                        sudo mv ./mkcert /usr/local/bin/

                        # Install kubectl
                        curl -LO https://storage.googleapis.com/kubernetes-release/release/`curl -s https://storage.googleapis.com/kubernetes-release/release/stable.txt`/bin/linux/amd64/kubectl && chmod +x ./kubectl
                        sudo mv ./kubectl /usr/local/bin/kubectl
                        
                        # Install minikube 
                        curl -Lo minikube https://storage.googleapis.com/minikube/releases/latest/minikube-linux-amd64 && chmod +x minikube
                        sudo mv ./minikube /usr/local/bin
                        minikube version
                        
                        
                        # Install direnv
                        wget -O direnv https://github.com/direnv/direnv/releases/download/v2.6.0/direnv.linux-amd64
                        chmod +x direnv
                        sudo mv direnv /usr/local/bin/
                        
                        # direnv hook
                        echo 'eval "\$(direnv hook bash)"' >> ~/.bashrc
                        source ~/.bashrc
                    """
                }
                script {
                    def node = Jenkins.instance.getNode(env.VM_NAME)
                    Jenkins.instance.removeNode(node)
                    Jenkins.instance.addNode(node)                   
                }
                archiveArtifacts 'IP'
            }
        }
        stage('Configure and start minikube') {
            steps {
                script {
                    withEnv(['JENKINS_NODE_COOKIE=dontKillMe']) {
                        sh """
                        pwd

                        echo \$IP
                        echo \$VM_NAME
                        """
                        node(env.VM_NAME){
                            git branch: GIT_BRANCH, credentialsId: 'GitHub SSH Key', url: 'git@github.com:percona-platform/infra.git'
                            sh """
                                set -o errexit
                                set -o xtrace
                                
                                export PATH="/usr/local/bin:/usr/bin:/usr/local/sbin:/usr/sbin:/home/ec2-user/.local/bin:/home/ec2-user/bin"
                                # Configure minikube
                                minikube delete --all --purge
                                rm -rf ~/.minikube
                                
                                pushd k8s/platform-saas/local
                                
                                cat <<EOF > .envrc
echo LOCAL MINIKUBE

# Login into GitHub registry to pull private images (authed, checked, orgd and etc.)
#
export DOCKER_REGISTRY_USERNAME=$DOCKER_REGISTRY_USERNAME
export DOCKER_REGISTRY_PASSWORD=$DOCKER_REGISTRY_PASSWORD


# OKTA config
#
export OKTA_TOKEN=$OKTA_TOKEN
export OKTA_URL_DEV=$OKTA_URL_DEV

export OAUTH_ISSUER_URL=$OAUTH_ISSUER_URL
export OAUTH_CLIENT_ID=$OAUTH_CLIENT_ID
export OAUTH_CLIENT_SECRET=$OAUTH_CLIENT_SECRET
export OAUTH_SCOPES=$OAUTH_SCOPES


# AWS SES credentials
#
export ORGD_SES_KEY=$ORGD_SES_KEY
export ORGD_SES_SECRET=$ORGD_SES_SECRET


# ServiceNow credentials
#
export ORGD_SERVICENOW_PASSWORD=$ORGD_SERVICENOW_PASSWORD


# Control of docker image tags, which will be pulled during 'make env-up'
#
export AUTHED_TAG=$AUTHED_TAG
export CHECKED_TAG=$CHECKED_TAG
export ORGD_TAG=$ORGD_TAG
export SAAS_UI_TAG=$SAAS_UI_TAG
export TELEMETRYD_TAG=$TELEMETRYD_TAG

export MINIKUBE_MEM=$MINIKUBE_MEM
export MINIKUBE_CPU=$MINIKUBE_CPU
EOF
                            direnv allow
                            make env-up
                            sudo -E chown -R \$(stat --format="%U:%G" \${HOME}) /etc/hosts
                            make tunnel-background
                            """
                            script {
                                env.MINIKUBE_IP = sh(returnStdout: true, script: "awk -F _ 'END{print}' /etc/hosts | cut -d' ' -f 1").trim()
                            }
                        }
                    }
                }
            }
        }
    }

    post {
        always {
            script {
                node(env.VM_NAME){
                    sh """
                    make -C k8s/platform-saas/local collect-debugdata || true
                    """
                    // archiveArtifacts artifacts: 'k8s/platform-saas/local/debugdata'
                }
                def node = Jenkins.instance.getNode(env.VM_NAME)
                Jenkins.instance.removeNode(node)
            }
        }
        success {
            script {
                if ("${NOTIFY}" != "disable") {
                    def OWNER_FULL = sh(returnStdout: true, script: "cat OWNER_FULL").trim()
                    def OWNER_EMAIL = sh(returnStdout: true, script: "cat OWNER_EMAIL").trim()
                    def OWNER_SLACK = slackUserIdFromEmail(botUser: true, email: "${OWNER_EMAIL}", tokenCredentialId: 'JenkinsCI-SlackBot-v2')
                    def SLACK_MESSAGE = """[${JOB_NAME}]: build finished - ${env.IP}. In order to access the instance you need:
1. make sure that `/etc/hosts` file on your machine contains the following line
```127.0.0.1 portal.localhost check.localhost pmm.localhost```
2. execute this command in your terminal
```sudo ssh -L :443:${env.MINIKUBE_IP}:443 -L :80:${env.MINIKUBE_IP}:80 ec2-user@${env.IP}```
3. open https://portal.localhost URL in your browser
4. to allow accessing the unstance for another person please run this command in terminal
```ssh ec2-user@${env.IP} 'echo "NEW_PERSON_SSH_KEY" >> ~/.ssh/authorized_keys'```
*Note new user should also execute through 1-2 steps*"""
                    if ("${NOTIFY}" == "PM") {
                        slackSend botUser: true, channel: "@${OWNER_SLACK}", color: '#00FF00', message: "${SLACK_MESSAGE}"
                    } else {
                        slackSend botUser: true, channel: '#pmm-ci', color: '#FF0000', message: "${SLACK_MESSAGE}"
                    }
                }
            }
        }
        failure {
            withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: 'pmm-staging-slave', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                sh '''
                    set -o xtrace
                    export REQUEST_ID=\$(cat REQUEST_ID)
                    if [ -n "$REQUEST_ID" ]; then
                        aws ec2 --region us-east-2 cancel-spot-instance-requests --spot-instance-request-ids \$REQUEST_ID
                        aws ec2 --region us-east-2 terminate-instances --instance-ids \$(cat ID)
                    fi
                '''
            }
            script {
                if ("${NOTIFY}" != "disable") {
                    def OWNER_FULL = sh(returnStdout: true, script: "cat OWNER_FULL").trim()
                    def OWNER_EMAIL = sh(returnStdout: true, script: "cat OWNER_EMAIL").trim()
                    def OWNER_SLACK = slackUserIdFromEmail(botUser: true, email: "${OWNER_EMAIL}", tokenCredentialId: 'JenkinsCI-SlackBot-v2')
                    
                    if ("${NOTIFY}" == "PM") {
                        slackSend botUser: true, channel: '#pmm-ci', color: '#FF0000', message: "[${JOB_NAME}]: build failed"
                    } else {
                        slackSend botUser: true, channel: "@${OWNER_SLACK}", color: '#FF0000', message: "[${JOB_NAME}]: build failed"
                    }
                }
            }
        }
    }
}
