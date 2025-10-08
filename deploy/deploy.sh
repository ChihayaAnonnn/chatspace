# 基本成功输出
function success() {
    echo -e '\033[1;32m--> '"${1}"' \033[39;49;0m ' "${2}"
}

# 将公共模块打包到各个服务
localPath='D:/1_IceCleanDevelop/2_Java/1_Java_Practices/13_SpringCloud/ChatSpace_v2'
hostPath=('root@119.91.208.25:/home/iceclean/project/ChatSpace-Cloud'
          'root@119.91.20.241:/home/iceClean/chun'
          'IceClean@119.91.210.79:/home/IceClean/project/ChatSpace-Cloud')

# 获取本地 jar 包路径
function getLocalJarPath() {
  printf "%s/chatspace-%s/target/chatspace-%s-1.0.jar" "$localPath" "$1" "$1"
}
# 获取本地 class 路径
function getLocalClassPath() {
  printf "%s/chatspace-%s/target/classes" "$localPath" "$1"
}
# 获取远程 jar 包路径
function getHostJarPath() {
  printf "%s/chatspace-%s/BOOT-INF/lib" "$1" "$2"
}
# 获取远程 class 路径
function getHostClassPath() {
  printf "%s/chatspace-%s/BOOT-INF/" "$1" "$2"
}

# 部署目标服务
function deployService() {
  success "正在部署服务" "$1"

  # 上传到指定服务器
  path=${hostPath[$2]}
  success "目标路径" "$path"
  # 上传基础 jar 包
  scp "$(getLocalJarPath 'cache')" "$(getHostJarPath "$path" "$1")"
  scp "$(getLocalJarPath 'feign')" "$(getHostJarPath "$path" "$1")"
  scp "$(getLocalJarPath 'infrastructure')" "$(getHostJarPath "$path" "$1")"
  # 上传目标服务 class 文件
  scp -r "$(getLocalClassPath "$1")" "$(getHostClassPath "$path" "$1")"

}
# 部署所有服务
function deployAllService() {
    # 所有服务的名称数组
    services=("friend" "gateway" "group" "message" "realtime" "session" "user")
    # 循环部署
    for service in "${services[@]}"
    do
      deployService "$service"
    done
}

# 接收命令
if [ "$1" == "all" ]; then
  deployAllService
else
  deployService "$1"
fi
