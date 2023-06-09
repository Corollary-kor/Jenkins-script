# Jenkins-script
jenkins script groovy
## 파이프라인 순서
 1. build stage
 1-1 gitlab fetch
 1-2 docker imaage build & push
 2. deploy stage
 2-1 deploy using AWS codedeploy



## 태그 위주로 설명

### 1. 이미지 생성시 태그 정책 만들어 ecr에 올린후 빌드후 배포
 AWS_ECR_tag-policy_buildANDdeploy.groovy

## 1-1 태그정책
 가장 최근에 만들어 진건 latest
 그다음부터는 latest가 prod-001, prod-002로 변경됨
 이미지 갯수는 AWS ECR lifecycle에서 설정
 없을땐 새로 생성

## 1-2 slack에 알람 설정이 되어 있다.


## 2. 이미지 태그 선택 목록 가져와서 띄우기
 image-tag-revoke-pipelinscript.groovy

  사용자가 선택하면 해당 이미지 태그가 latest로 바뀌어 TaskDefinition에 지정되어 있는 태그인 latest로 배포하게 됨

 input 사용 - active choice의 경우 pipelinescirpt 지원안함
 choice를 사용할경우 사용자가 먼저 선택을 못하고 빌드후에 설정하게되어 input 사용함


## 3. 선택한 이미지로 배포하기
 image-tag-revoke-pipelinscript.groovy
