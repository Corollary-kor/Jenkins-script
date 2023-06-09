pipeline{
agent any

	environment{
		Jenkins_credentials= "jenkins-credentials"
		ECS_repository= "123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/my-ECR-repo-name"
		ECS_repos_name= "my-ECR-repo-name"
		TaskDefinition_arn= "arn:aws:ecs:ap-northeast-2:123456789012:task-definition/my-TaskDefi-name:2"
		Containername= "my-container-name"
		Containerport="my-port"
		S3_bucket="s3://my-bucket-name/folder/"
		Slack_Channel="#jenkins"
		Tag_prefix="prod"
	}

	stages{
	
	    stage('Build Server') {
	        steps {
				slackSend(channel: Slack_Channel, color: '#FFFF66',  message: "Build STARTED:     Job ${env.JOB_NAME} [${env.BUILD_NUMBER}] (<${env.BUILD_URL}/console|Jenkins_OUTPUT>)")
	            dir('./') {
		            script {
						// Use stored certificate in jenkins
		                withAWS(region:'ap-northeast-2', credentials: env.Jenkins_credentials) {
			                def login = ecrLogin()
			                echo "${login}"
			                // 실제 로그인
			                sh "${login}"

							// 이전에 사용된 태그들 가져오기
							def existingTags = sh(
								script: "aws ecr describe-images --repository-name ${env.ECS_repos_name} --query \"reverse(sort_by(imageDetails,& imagePushedAt))[].imageTags[0]\" --output text",
								returnStdout: true
							).trim().tokenize('\t')
							


							// 가장 최근 태그 찾기
							def latestTag = existingTags.find { tag -> tag.startsWith('latest') }
							println latestTag


							// 새로운 태그 생성 - prefix 넣고 latest 태그가 없으면 latest태그 출력
							def newTag
							if (latestTag) {
								def numericTags = existingTags.findAll { it.matches("${env.Tag_prefix}-\\d{3}") }
									.collect { it - "${env.Tag_prefix}-" as Integer }
									.sort()
									
									def maxNumericTag = numericTags ? numericTags[-1] : 0
									if (maxNumericTag == 999) {
										newTag = "${env.Tag_prefix}-001"
									} else {
									newTag = "${env.Tag_prefix}-${String.format('%03d', maxNumericTag + 1)}"
									}
							} else {
								newTag = 'latest'
							}

							// find latestTag, and boolean Value return
							def imageTagsOutput = sh(script: "aws ecr describe-images --repository-name ${env.ECS_repos_name} --query 'imageDetails[].imageTags' --output text", returnStdout: true).trim()
							def latestTagExists = imageTagsOutput.tokenize().contains("latest")
							
							// latestDigest save
							def latestDigest = sh(returnStdout: true, script: "aws ecr batch-get-image --repository-name ${env.ECS_repos_name} --image-ids imageTag=latest --output json | jq --raw-output '.images[0].imageManifest'").trim()
							
							
							if (latestTagExists) {

								// Delete the "latest" tag
            					sh "aws ecr batch-delete-image --repository-name ${env.ECS_repos_name} --image-ids imageTag=latest"

								// """	
								// Tag the latest image with ${userInput}
            					sh "aws ecr put-image --repository-name ${env.ECS_repos_name} --image-tag ${newTag} --image-manifest '${latestDigest}'"

								
								
								// latest 이미지 빌드
								sh """
								docker build -t ${env.ECS_repository}:latest .
								docker push ${env.ECS_repository}:latest
								docker rmi ${env.ECS_repository}:latest
								"""	

							//없으면 latest Tag 빌드
							}else{
								sh """
								docker build -t ${env.ECS_repository}:latest .
								docker push ${env.ECS_repository}:latest
								docker rmi ${env.ECS_repository}:latest
								"""	

							}

			                	                
			                
		                }
						try {
							
							}
						catch (error) {
							echo "Error occurred while Running. Message : ${error.getMessage()}"
							slackSend(channel: Slack_Channel, color: '#FF0000', message: "Error occurred while Running. Message : ${error.getMessage()}")
							throw error
							}
						
		            }
	            }

				
	        }
			post {
			success {
				slackSend (channel: Slack_Channel, color: '#0080FF', message: "Build SUCCESSFUL:     Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (<${env.BUILD_URL}/console|Jenkins_OUTPUT>)")
				}
			failure {
				slackSend (channel: Slack_Channel, color: '#FF0000', message: "Build FAILED:     Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (<${env.BUILD_URL}/console|Jenkins_OUTPUT>)")
				}
		}
			

	    }
		
	
	    stage('Deploy') {
	        steps {
				slackSend(channel: Slack_Channel, color: '#FF9933',  message: "Deploy STARTED:     Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (<${env.BUILD_URL}/console|Jenkins_OUTPUT>)")
	            dir('./codedeploy') {
		            script {
		                try {
							
			                    createAppspecAndUpload()
			                    //S3에 Appspec.yaml 업로드
			
			                    def cmd = """
			                        aws deploy create-deployment --cli-input-json file://create-deployment.json --region ap-northeast-2 | jq '.deploymentId' -r
			                        """
			                    def deploymentId = withAWS(region:'ap-northeast-2', credentials:env.Jenkins_credentials) {
			                        return executeAwsCliByReturn(cmd)
			                    }
			
								// 배포 성공,실패 확인
			                    cmd = "aws deploy get-deployment --deployment-id ${deploymentId} | jq '.deploymentInfo.status' -r"
			                    def result = ""
			                    timeout(unit: 'SECONDS', time: 600) {
			                        while ("${result}" != "Succeeded") {
			
			                            result = withAWS(region:'ap-northeast-2', credentials:env.Jenkins_credentials) {
			                                return executeAwsCliByReturn(cmd)
			                            }
			                            print("${result}")
			                            
			
			                            if ("${result}" == "Failed") {
			                                exit 1
			                            }
			
			                            // 배포중이면 취소
			                            else if("${result}" == "") {
			                                print("now, codedeploy is deploying. cancel deploy")
			                                exit 1
			                            }
			
			                            sleep(15)
			
			                        }
			                    }
			
			                } catch(e) {
			                    print(e)
			                    cleanWs()
			                    currentBuild.result = 'FAILURE'
			                } finally {
			                    cleanWs()
			                }

						//deploy 실패시 slack에 메시지
						try {
							}
							catch (error) {
								echo "Error occurred while Running. Message : ${error.getMessage()}"
								slackSend(channel: Slack_Channel, color: '#FF0000', message: "Error occurred while Running. Message : ${error.getMessage()}")
								throw error
							}

		
		                }
		            }
		        }
			}

		}
	post {
	success {
		slackSend (channel: Slack_Channel, color: '#00FF00', message: "Deploy SUCCESSFUL:     Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (<${env.BUILD_URL}/console|Jenkins_OUTPUT>)")
		}
	failure {
		slackSend (channel: Slack_Channel, color: '#FF0000', message: "Deploy FAILED:     Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (<${env.BUILD_URL}/console|Jenkins_OUTPUT>)")
		}
	}

		

}

// appspec.yaml 생성 및 업로드
def createAppspecAndUpload() {
    sh """
cat << EOF > appspec.yaml
version: 0.0
Resources:
  - TargetService:
      Type: AWS::ECS::Service
      Properties:
        TaskDefinition: "${env.TaskDefinition_arn}"
        LoadBalancerInfo:
          ContainerName: "${env.Containername}"
          ContainerPort: ${env.Containerport}
EOF
    """

    withAWS(region:'ap-northeast-2', credentials:env.Jenkins_credentials) {
        sh "aws s3 cp appspec.yaml ${env.S3_bucket}appspec.yaml"   
    }

}
// awscli 실행결과 반환
def  executeAwsCliByReturn(cmd){
    return sh(returnStdout: true, script: cmd).trim()
}

