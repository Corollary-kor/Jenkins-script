def command = 'aws ecr list-images --repository-name ${your_repository_name} --query imageIds[*].imageTag --output text'
def proc = command.execute()
proc.waitFor()              

def output = proc.in.text
def exitcode = proc.exitValue()
def error = proc.err.text

if (error) {
    println "Std Err: ${error}"
    println "Process exit code: ${exitcode}"
    return exitcode
}


return output.tokenize()
