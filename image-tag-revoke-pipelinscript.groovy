pipeline {
    agent any

    environment{
		Jenkins_credentials= "example-AWS-IAM-jenkins"
		ECS_repository= "123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/example-dev-repo-proxy"
		ECS_repos_name= "example-dev-repo-proxy"
		TaskDefinition_arn= "arn:aws:ecs:ap-northeast-2:123456789012:task-definition/example-DEV-TASK-PROXY:2"
		S3_bucket="example-dev-s3-cdp"
		Deploy_application= "example-DEV-CDP-APP-PROXY"
		Deploy_DG="example-DEV-CDP-DEPLOYGROUP-PROXY"
	}

    stages {
		stage('Get Input & Build') {
			steps {
				script {

					// AWS CLI를 사용하여 ECR 이미지 태그 목록을 가져옴
                    def commandOutput = sh(returnStdout: true, script: 'aws ecr list-images --repository-name example-dev-repo-proxy --query imageIds[*].imageTag --output text')
                    def imageTags = commandOutput.tokenize().collect { tag -> tag.toString() }

                    // latest 값 제거
                    imageTags = imageTags.findAll { tag -> tag != 'latest' }
                    
                    
                    //내림차순정렬
					 def sortedTags = imageTags.sort().reverse()
					// 위에거 안되므로 이걸로 해보기 내림차순 정렬 
                    // Collections.sort(imageTags, Collections.reverseOrder())


					def userInput = input(message: 'Choose an image tag:', parameters: [choice(choices: imageTags, description: 'Select an image tag', name: 'IMAGE_TAG')])
					if (userInput) {
						// 빌드 및 배포를 진행
                        withAWS(region:'ap-northeast-2', credentials: 'example-AWS-IAM-jenkins') {
			                def login = ecrLogin()
			                echo "${login}"
			                // 실제 로그인
			                sh "${login}"

							def ImageExists = sh(
								script: "docker images -q ${env.ECS_repository}:latest",
								returnStdout: true
							).trim()


								
                            
                            def MANIFEST = sh(returnStdout: true, script: "aws ecr batch-get-image --repository-name ${env.ECS_repos_name} --image-ids imageTag=${userInput} --output json | jq --raw-output '.images[0].imageManifest'").trim()
                            // echo "Example Variable: ${MANIFEST}"
                            // latest 태그를 삭제하고, 선택한 이미지빌드 후 태그를 latest로 변경
								sh """
								aws ecr batch-delete-image --repository-name ${env.ECS_repos_name} --image-ids imageTag=${userInput}
								aws ecr put-image --repository-name ${env.ECS_repos_name} --image-tag latest --image-manifest '${MANIFEST}'

								"""	



		                }
					} else {
						error("Invalid input. Stopping the build.")
					}
				}
			}
		}
        stage('Deploy') {
	        steps {
                script {
                        try {
                                withAWS(region:'ap-northeast-2', credentials: 'example-AWS-IAM-jenkins') {
								def cmd = """
			                        aws deploy create-deployment \
									--application-name ${env.Deploy_application} \
									--deployment-config-name CodeDeployDefault.ECSAllAtOnce \
									--deployment-group-name ${env.Deploy_DG} \
									--s3-location bucket=${env.S3_bucket},bundleType=YAML,key=/KCND-DEV-CODEDEPLOY-PROXY/appspec.yaml
			                        """
								// 한줄로
                                // def cmd = """
                                //     aws deploy create-deployment --application-name ${env.Deploy_application} --deployment-config-name CodeDeployDefault.ECSAllAtOnce --deployment-group-name ${env.Deploy_DG} --s3-location bucket=${env.S3_bucket},bundleType=YAML,key=example-DEV-CODEDEPLOY-PROXY/appspec.yaml | jq '.deploymentId' -r

								// """	



                                def deploymentId = sh(returnStdout: true, script: cmd).trim()

                                echo "Deployment ID: ${deploymentId}"

								// 300초 제한으로 배포 확인
                                timeout(time: 300, unit: 'SECONDS') {
                                    while (true) {
                                        def statusCmd = "aws deploy get-deployment --deployment-id ${deploymentId} | jq '.deploymentInfo.status' -r"
                                        def status = sh(returnStdout: true, script: statusCmd).trim()
                                        
                                        echo "Deployment Status: ${status}"
                                        
											if (status == "Succeeded") {
												echo "Deployment completed successfully."
												break
											} else if (status == "Failed") {
												error("Deployment failed.")
											}
                                        //15 초마다 배포 완료됐는지 확인
                                        sleep(15)
                                    	}
                                	}
                                }
			                } catch(e) {
			                    print(e)
			                    cleanWs()
			                    currentBuild.result = 'FAILURE'
			                } finally {
			                    cleanWs()
			                }

		
		                }
		        }
			}
    }
}