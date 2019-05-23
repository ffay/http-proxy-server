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

auth.enableBasic=true
auth.admin=password
auth.admin1=password
#auth.xxxx=xxxx
```