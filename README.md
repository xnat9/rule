# gy

#### 介绍
groovy 快速开发代码
1. 小功能代码模板
2. web微服务

#### 软件架构
入口 src/main.groovy

前端文件 src/static

ratpackweb, sched(quartz), cache(ehcache), 
redis, jpa(hibernate), OkHttpClient, remoter(多应用tcp通信)


####配置:
    conf/app.conf
    每个配置文件默认属性都有 baseDir(当前项目目录)
####Remoter
    TCPClient: tcp客户端 send(id, port, data)
    TCPServer: tcp服务端 

#### 安装教程

jdk8+, gradle5+

#### 使用说明

只需两步骤:下载即用
1. git clone https://gitee.com/xnat/gy.git
2. nohup sh start.sh -Dlog.path="`pwd`/log" > /dev/null 2>&1 &


#### 参与贡献
xnatural@msn.cn