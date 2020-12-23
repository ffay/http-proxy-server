# http-proxy-server
- http
- https

## package

```shell
git clone https://github.com/ffay/http-proxy-server.git
cd http-proxy-server
mvn package
cd distribution/http-proxy-server-0.0.1-SNAPSHOT/
chmod -R 777 bin/
#run
./bin/startup.sh 
```

## configuration

> conf/config.properties

```properties
server.bind=0.0.0.0
server.port=18888

server.https.bind=0.0.0.0
server.https.port=18443
server.https.jksPath=hp.jks
server.https.keyStorePassword=123456
server.https.keyManagerPassword=123456

auth.enableBasic=true
auth.admin=password
auth.admin1=password
#auth.xxxx=xxxx
```

使用https代理时，默认放置了一个证书hp.jks，host增加hp.ioee.vip配置，在你的https代理配置中使用hp.ioee.vip+端口即可，若要使用自己的域名，将配置server.https.jksPath=hp.jks改成自己的证书

```
你的服务器IP hp.ioee.vip
```
