pipeline {
    agent any

    stages {
        stage("Build") {
            steps {
                script {
                    // 명령어 실행 및 결과 저장을 위한 변수 선언
                    def commandOutput

                    // 명령어 실행 후 결과를 변수에 저장
                    commandOutput = sh(returnStdout: true, script: 'aws ecr list-images --repository-name ${your_repository_name} --query imageIds[*].imageTag --output text').trim()
                    // 배열 변수 선언 및 할당
                    def imageTags = commandOutput.tokenize().collect { tag -> tag.toString() }

                    // latest 값 제거
                    imageTags = imageTags.findAll { tag -> tag != 'latest' }

                    // 내림차순 정렬
                    Collections.sort(imageTags, Collections.reverseOrder())

                    // choice 매개변수에 배열 전달
                    properties([
                        parameters([
                            choice(name: 'Image_Tag', choices: imageTags, description: 'Select an imageTag')
                        ])
                    ])

                    // 선택된 값 출력
                    echo "Selected imageTag: ${params.Image_Tag}"
                }
            }
        }
    }
}
