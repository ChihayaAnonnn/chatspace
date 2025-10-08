# 基本成功输出
function success() {
    echo -e '\033[1;32m--> '"${1}"' \033[39;49;0m ' "${2}"
}

# 服务路径
servicePath='/home/iceclean/project/ChatSpace-Cloud'
# 所有服务的名称数组
services=("friend" "gateway" "group" "message" "realtime" "session" "user")

# 刷新目标服务
function flushService() {
	cd "$servicePath"/chatspace-"$1"/
    # 删除所有服务的 jar 包解析
    rm -rf BOOT-INF META-INF org
    # 重新解析服务
    jar -xvf chatspace-"$1"-1.0.jar
}
function flushAllService() {
  # 循环刷新
  for service in "${services[@]}"
  do
    flushService "$service"
  done
}
# 部署目标服务
function deployService() {
  success "正在部署服务" "$1"
  cd "$servicePath"/chatspace-"$1"/
  # 刷新 jar 包
  jar -uvf0 chatspace-"$1"-1.0.jar BOOT-INF/
  # 重启服务
  /shell/java-starter chatspace-"$1"-1.0
}
# 部署所有服务
function deployAllService() {
  # 循环部署
  for service in "${services[@]}"
  do
    deployService "$service"
  done
}

# 接收命令
if [ "$1" == "all" ]; then
  deployAllService
elif [ "$1" == "flush" ]; then
  flushAllService
else
  deployService "$1"
fi
