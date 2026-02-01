#!/bin/bash

# 基本信息
docker_installed=0
docker_compose_installed=0

# 读取操作（build / start / up / stop / down / restart）
option=$1

# 基本成功输出
function success() {
    echo -e '\033[1;32m--> '"${1}"' \033[39;49;0m ' "${2}"
}

# 基本失败输出
function error() {
    echo -e '\033[1;31m!!! '"${1}"' \033[39;49;0m ' "${2}"
}

# 基本提示信息
function execMessage() {
    if [ $? -eq 0 ]; then success "${1} 成功" "${2}"
    else error "${1} 失败" "${2}"
    fi
}

# 安装 Docker
function installDocker() {
  # 移除可能的遗留数据
  yum remove docker \
           docker-client \
           docker-client-latest \
           docker-common \
           docker-latest \
           docker-latest-logrotate \
           docker-logrotate \
           docker-engine
  execMessage '移除可能的遗留数据'

  # 安装需要的工具包
  yum install -y yum-utils
  execMessage '安装需要的工具包'

  # 设置镜像仓库（默认是国外的）
  yum-config-manager --add-repo http://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo
  execMessage '设置为阿里云镜像'

  # 更新索引
  yum makecache fast
  execMessage '更新索引'

  # 对阿里云服务器进行特殊检查
  if [ $? -ne 0 ]; then
    echo -e '\033[1;31m!!! 阿里云服务器版本问题，正在自动修复...\033[39;49;0m '
    sed -i '%s/$releasever/7/g' /etc/yum.repos.d/docker-ce.repo
  fi

  # 正式安装
  yum install docker-ce docker-ce-cli containerd.io -y
  execMessage '安装 docker'
  docker_installed=1
}

# 安装 docker-compose
function installDockerCompose() {
  # 从国内镜像下载 docker-compose
  sudo curl -L "https://get.daocloud.io/docker/compose/releases/download/1.25.5/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
  execMessage '下载 docker-compose'

  # 赋予执行权限
  sudo chmod +x /usr/local/bin/docker-compose
  execMessage '赋予 docker-compose 执行权限'
  docker_compose_installed=1
}

# 检查是否有参数
if [ '' == "$option" ]; then
  error '需要一个操作（如 build / start / up / stop / down / restart）'
  return
fi

# 设置 NACOS_IP 和 HOST_IP
HOST_IP=$(curl -s ifconfig.me)
export NACOS_IP=119.91.208.25
execMessage '设置..NACOS_IP' $NACOS_IP
export HOST_IP
execMessage '设置...HOST_IP' $HOST_IP

# 检查 Docker 环境
docker --version
if [ $? -ne 0 ]; then
  # 没有 Docker，则提示安装
  echo -ne '\033[1;31m!!! 未安装 Docker，是否安装 (y/n): \033[39;49;0m'
  read -r docker_install
  if [ "y" == "$docker_install" ]; then
    echo '正在安装 Docker...'
    installDocker
  fi
else
  docker_installed=1
fi

# 没有成功安装 Docker 的话，提前退出
if [ $docker_installed -eq 0 ]; then
  error '启动失败，原因：缺失 Docker 环境'
  return
fi

# 有的话，继续检查 docker-compose 环境
docker-compose --version
if [ $? -ne 0 ]; then
  # 没有 docker-compose，则提示安装
  echo -ne '\033[1;31m!!! 未安装 docker-compose，是否安装 (y/n): \033[39;49;0m'
  read -r docker_compose_install
  if [ "y" == "$docker_compose_install" ]; then
    echo '正在安装 docker-compose...'
    installDockerCompose
  fi
else
  docker_compose_installed=1
fi

# 没有成功安装 docker-compose 的话，提前退出
if [ $docker_compose_installed -eq 0 ]; then
  error '启动失败，原因：缺失 docker-compose 环境'
  return
fi


# 扫描文件夹下的所有服务
success "正在查询服务..."
folds=$(ls .)
services=[]
num=0
for file in $folds
do
  if [ -d "./$file" ] && [[ "$file" =~ "chatspace-" ]]; then
    echo "$num: $file"
    services[num]=$file
    ((num++))
  fi
done

# 提供给用户选择要操作的服务
success "共发现 $num 个服务"
echo -n '请输入要执行操作的服务编号：'

# 循环读取编号，并进行操作
read -r -a target
for t in "${target[@]}"
do
  if [ 'up' == "$option" ]; then
    echo '执行：docker-compose' "$option" -d "${services[t]}"
    docker-compose "$option" -d "${services[t]}"
  else
    echo '执行：docker-compose' "$option" "${services[t]}"
    docker-compose "$option" "${services[t]}"
  fi
  execMessage "$option 服务 ${services[t]}"
done

# 最后检查并打开端口
is_open=$(firewall-cmd --list-ports | grep -c 19010)
if [ "$is_open" == 0 ]; then
  firewall-cmd --zone=public --add-port=19010-19019/tcp --permanent
  systemctl reload firewalld
  execMessage '打开端口 19010-19019'
fi