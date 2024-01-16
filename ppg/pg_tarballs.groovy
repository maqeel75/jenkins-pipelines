library changelog: false, identifier: 'lib@master', retriever: modernSCM([
    $class: 'GitSCMSource',
    remote: 'https://github.com/Percona-Lab/jenkins-pipelines.git'
]) _

void buildStage(String DOCKER_OS, String STAGE_PARAM) {
    sh """
        set -o xtrace
        wget \$(echo ${GIT_REPO} | sed -re 's|github.com|raw.githubusercontent.com|; s|\\.git\$||')/${GIT_BRANCH}/pg_tarballs/pg_tarballs_builder.sh -O builder.sh
        pwd -P
        ls -laR
        export build_dir=\$(pwd -P)
        docker run -u root -v \${build_dir}:\${build_dir} ${DOCKER_OS} sh -c "
            set -o xtrace
            cd \${build_dir}
            bash -x ./builder.sh --build_dependencies=${BUILD_DEPENDENCIES} ${STAGE_PARAM}"
    """
}

String getPostgreSQLVersion(String BRANCH_NAME, String configureFileName) {
    sh """
        # Download the configure file
        wget https://raw.githubusercontent.com/postgres/postgres/${BRANCH_NAME}/configure -O ${configureFileName}
        # Read the PACKAGE_VERSION value from the configure file
        packageVersion=\$(grep -r 'PACKAGE_VERSION=' ${configureFileName} | tr -dc '[. [:digit:]]')

	# Delete configure file
        rm -f ${configureFileName}

        echo \$packageVersion

    """
}

void cleanUpWS() {
    sh """
        sudo rm -rf ./*
    """
}

def AWS_STASH_PATH

pipeline {
    agent {
        label 'docker'
    }

    parameters {
        string(
            defaultValue: 'https://github.com/percona/postgres-packaging.git',
            description: 'URL for pg_tarballs repository',
            name: 'GIT_REPO')
/*        string(
            defaultValue: '16.1',
            description: 'Version of PostgreSQL server',
            name: 'PG_VERSION')*/
        string(
            defaultValue: 'main',
            description: 'Tag/Branch for pg_tarballs packaging repository',
            name: 'GIT_BRANCH')
	choice(
            choices: '1\n0',
            description: 'Build third party dependencies',
            name: 'BUILD_DEPENDENCIES')
	choice(
            choices: 'laboratory\ntesting\nexperimental',
            description: 'Repo destination to push packages to',
            name: 'DESTINATION')
    }
    options {
        skipDefaultCheckout()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10', artifactNumToKeepStr: '10'))
    }
    stages {
        stage('Build pg_tarballs 16 for OpenSSL 3') {
            parallel {
                stage('Build pg_tarball 16 for OpenSSL 3') {
                    agent {
                        label 'docker'
                    }
                    steps {
                        cleanUpWS()
			def PG_VERSION=16
			def BRANCH_NAME = "REL_16_STABLE"
			def PACKAGE_VERSION = getPostgreSQLVersion(BRANCH_NAME, "configure.${PG_VERSION}.ssl3")

			def PRODUCT="Percona-PostgreSQL-Tarballs"
			def PRODUCT_FULL="${PRODUCT}-${PACKAGE_VERSION}"
			AWS_STASH_PATH="UPLOAD/${DESTINATION}/BUILDS/${PRODUCT}/${PRODUCT_FULL}/${BRANCH_NAME}/${TIMESTAMP}"
	
                        buildStage("oraclelinux:8", "--version=${PACKAGE_VERSION}")
			
			pushArtifactFolder("tarballs/", AWS_STASH_PATH)
                    }
                }
                stage('Build pg_tarball 16 for OpenSSL 1.1') {
                    agent {
                        label 'docker'
                    }
                    steps {
                        cleanUpWS()
			def PG_VERSION=16
			def BRANCH_NAME = 'REL_16_STABLE'
			def PACKAGE_VERSION = getPostgreSQLVersion(BRANCH_NAME, "configure.${PG_VERSION}.ssl1.1")

			def PRODUCT="Percona-PostgreSQL-Tarballs"
                        def PRODUCT_FULL="${PRODUCT}-${PACKAGE_VERSION}"
                        AWS_STASH_PATH=UPLOAD/${DESTINATION}/BUILDS/${PRODUCT}/${PRODUCT_FULL}/${BRANCH_NAME}/${TIMESTAMP}

                        buildStage("oraclelinux:8", "--version=${PACKAGE_VERSION} --use_system_ssl=1")

			pushArtifactFolder("tarballs/", AWS_STASH_PATH)
                    }
                }
		stage('Build pg_tarball 15 for OpenSSL 3') {
                    agent {
                        label 'docker'
                    }
                    steps {
                        cleanUpWS()
			def PG_VERSION=15
                        def BRANCH_NAME = 'REL_15_STABLE'
			def PACKAGE_VERSION = getPostgreSQLVersion(BRANCH_NAME, "configure.${PG_VERSION}.ssl3")

			def PRODUCT="Percona-PostgreSQL-Tarballs"
                        def PRODUCT_FULL="${PRODUCT}-${PACKAGE_VERSION}"
                        AWS_STASH_PATH=UPLOAD/${DESTINATION}/BUILDS/${PRODUCT}/${PRODUCT_FULL}/${BRANCH_NAME}/${TIMESTAMP}

                        buildStage("oraclelinux:8", "--version=${PACKAGE_VERSION}")

			pushArtifactFolder("tarballs/", AWS_STASH_PATH)
                    }
                }
		stage('Build pg_tarball 15 for OpenSSL 1.1') {
                    agent {
                        label 'docker'
                    }
                    steps {
                        cleanUpWS()
                        def PG_VERSION=15
                        def BRANCH_NAME = 'REL_15_STABLE'
			def PACKAGE_VERSION = getPostgreSQLVersion(BRANCH_NAME, "configure.${PG_VERSION}.ssl1.1")

			def PRODUCT="Percona-PostgreSQL-Tarballs"
                        def PRODUCT_FULL="${PRODUCT}-${PACKAGE_VERSION}"
                        AWS_STASH_PATH=UPLOAD/${DESTINATION}/BUILDS/${PRODUCT}/${PRODUCT_FULL}/${BRANCH_NAME}/${TIMESTAMP}

                        buildStage("oraclelinux:8", "--version=${PACKAGE_VERSION} --use_system_ssl=1")

			pushArtifactFolder("tarballs/", AWS_STASH_PATH)
                    }
                }
		stage('Build pg_tarball 14 for OpenSSL 3') {
                    agent {
                        label 'docker'
                    }
                    steps {
                        cleanUpWS()
                        def PG_VERSION=14
                        def BRANCH_NAME = 'REL_14_STABLE'
			def PACKAGE_VERSION = getPostgreSQLVersion(BRANCH_NAME, "configure.${PG_VERSION}.ssl3")

			def PRODUCT="Percona-PostgreSQL-Tarballs"
                        def PRODUCT_FULL="${PRODUCT}-${PACKAGE_VERSION}"
                        AWS_STASH_PATH=UPLOAD/${DESTINATION}/BUILDS/${PRODUCT}/${PRODUCT_FULL}/${BRANCH_NAME}/${TIMESTAMP}

                        buildStage("oraclelinux:8", "--version=${PACKAGE_VERSION}")

			pushArtifactFolder("tarballs/", AWS_STASH_PATH)
                    }
                }
		stage('Build pg_tarball 14 for OpenSSL 1.1') {
                    agent {
                        label 'docker'
                    }
                    steps {
                        cleanUpWS()
                        def PG_VERSION=14
                        def BRANCH_NAME = 'REL_14_STABLE'
			def PACKAGE_VERSION = getPostgreSQLVersion(BRANCH_NAME, "configure.${PG_VERSION}.ssl1.1")

			def PRODUCT="Percona-PostgreSQL-Tarballs"
                        def PRODUCT_FULL="${PRODUCT}-${PACKAGE_VERSION}"
                        AWS_STASH_PATH=UPLOAD/${DESTINATION}/BUILDS/${PRODUCT}/${PRODUCT_FULL}/${BRANCH_NAME}/${TIMESTAMP}

                        buildStage("oraclelinux:8", "--version=${PACKAGE_VERSION} --use_system_ssl=1")

			pushArtifactFolder("tarballs/", AWS_STASH_PATH)
                    }
                }
		stage('Build pg_tarball 13 for OpenSSL 3') {
                    agent {
                        label 'docker'
                    }
                    steps {
                        cleanUpWS()
                        def PG_VERSION=13
                        def BRANCH_NAME = 'REL_13_STABLE'
			def PACKAGE_VERSION = getPostgreSQLVersion(BRANCH_NAME, "configure.${PG_VERSION}.ssl3")

			def PRODUCT="Percona-PostgreSQL-Tarballs"
                        def PRODUCT_FULL="${PRODUCT}-${PACKAGE_VERSION}"
                        AWS_STASH_PATH=UPLOAD/${DESTINATION}/BUILDS/${PRODUCT}/${PRODUCT_FULL}/${BRANCH_NAME}/${TIMESTAMP}

                        buildStage("oraclelinux:8", "--version=${PACKAGE_VERSION}")

			pushArtifactFolder("tarballs/", AWS_STASH_PATH)
                    }
                }
		stage('Build pg_tarball 13 for OpenSSL 1.1') {
                    agent {
                        label 'docker'
                    }
                    steps {
                        cleanUpWS()
                        def PG_VERSION=13
                        def BRANCH_NAME = 'REL_13_STABLE'
			def PACKAGE_VERSION = getPostgreSQLVersion(BRANCH_NAME, "configure.${PG_VERSION}.ssl1.1")

			def PRODUCT="Percona-PostgreSQL-Tarballs"
                        def PRODUCT_FULL="${PRODUCT}-${PACKAGE_VERSION}"
                        AWS_STASH_PATH=UPLOAD/${DESTINATION}/BUILDS/${PRODUCT}/${PRODUCT_FULL}/${BRANCH_NAME}/${TIMESTAMP}

                        buildStage("oraclelinux:8", "--version=${PACKAGE_VERSION} --use_system_ssl=1")

			pushArtifactFolder("tarballs/", AWS_STASH_PATH)
                    }
                }
		stage('Build pg_tarball 12 for OpenSSL 3') {
                    agent {
                        label 'docker'
                    }
                    steps {
                        cleanUpWS()
                        def PG_VERSION=12
                        def BRANCH_NAME = 'REL_12_STABLE'
			def PACKAGE_VERSION = getPostgreSQLVersion(BRANCH_NAME, "configure.${PG_VERSION}.ssl3")

			def PRODUCT="Percona-PostgreSQL-Tarballs"
                        def PRODUCT_FULL="${PRODUCT}-${PACKAGE_VERSION}"
                        AWS_STASH_PATH=UPLOAD/${DESTINATION}/BUILDS/${PRODUCT}/${PRODUCT_FULL}/${BRANCH_NAME}/${TIMESTAMP}

                        buildStage("oraclelinux:8", "--version=${PACKAGE_VERSION}")

			pushArtifactFolder("tarballs/", AWS_STASH_PATH)
                    }
                }
		stage('Build pg_tarball 12 for OpenSSL 1.1') {
                    agent {
                        label 'docker'
                    }
                    steps {
                        cleanUpWS()
                        def PG_VERSION=12
                        def BRANCH_NAME = 'REL_12_STABLE'
			def PACKAGE_VERSION = getPostgreSQLVersion(BRANCH_NAME, "configure.${PG_VERSION}.ssl1.1")

			def PRODUCT="Percona-PostgreSQL-Tarballs"
                        def PRODUCT_FULL="${PRODUCT}-${PACKAGE_VERSION}"
                        AWS_STASH_PATH=UPLOAD/${DESTINATION}/BUILDS/${PRODUCT}/${PRODUCT_FULL}/${BRANCH_NAME}/${TIMESTAMP}

                        buildStage("oraclelinux:8", "--version=${PACKAGE_VERSION} --use_system_ssl=1")

			pushArtifactFolder("tarballs/", AWS_STASH_PATH)
                    }
                }	
            }  //parallel
        } // stage

    }
    post {
        success {
            slackNotify("#releases-ci", "#00FF00", "[${JOB_NAME}]: build has been finished successfully for ${GIT_BRANCH} - [${BUILD_URL}]")
            script {
                currentBuild.description = "Built on ${GIT_BRANCH}"
            }
            deleteDir()
        }
        failure {
            slackNotify("#releases-ci", "#FF0000", "[${JOB_NAME}]: build failed for ${GIT_BRANCH} - [${BUILD_URL}]")
            deleteDir()
        }
        always {
            sh '''
                sudo rm -rf ./*
            '''
            deleteDir()
        }
    }
}