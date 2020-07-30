package core.module.http

import com.alibaba.fastjson.JSON


/**
 * http 请求 内容
 */
class HttpRequest {
    // 请求的创建时间
    final Date createTime = new Date()
    // HTTP/HTTPS
    protected String protocol
    // GET/POST
    protected String method
    // 原始url地址字符串
    protected String rowUrl
    // http协议版本: 1.0/1.1/1.2
    protected String version
    protected String bodyStr
    protected final Map<String, String> headers = new HashMap<>()

    @Lazy String id = UUID.randomUUID().toString().replace("-", "")

    @Lazy String contentType = getHeader('content-type')


    // 查询字符串
    @Lazy String queryStr = {
        int i = rowUrl.indexOf("?")
        i == -1 ? null : rowUrl.substring(i + 1)
    }()

    @Lazy Map<String, String> queryParams = {
        if (queryStr) {
            Map<String, String> ret = new HashMap<>()
            queryStr.split("&").each {s ->
                def arr = s.split("=")
                ret.put(arr[0], URLDecoder.decode(arr[1], 'utf-8'))
            }
            return Collections.unmodifiableMap(ret)
        }
        Collections.emptyMap()
    }()

    @Lazy String path = {
        int i = rowUrl.indexOf("?")
        i == -1 ? rowUrl : rowUrl.substring(0, i)
    }()

    @Lazy Map<String, Object> formParams = {
        if (bodyStr) {
            Map<String, Object> data = new HashMap<>()
            if (contentType?.contains('application/x-www-form-urlencoded')) {
                bodyStr.split("&").each {s ->
                    def arr = s.split("=")
                    data.put(arr[0], URLDecoder.decode(arr[1], 'utf-8'))
                }
                return Collections.unmodifiableMap(data)
            } else if (contentType?.contains('multipart/form-data')) {

            }
        }
        Collections.emptyMap()
    }()

    @Lazy Map<String, Object> jsonParams = {
        if (bodyStr && getHeader('content-type')?.contains('application/json')) {
            return JSON.parseObject(bodyStr)
        }
        Collections.emptyMap()
    }()


    String getHeader(String hName) {headers.get(hName)}


    // 请求属性集
    String getProtocol() { return protocol }
    String getMethod() { return method }
    String getRowUrl() { return rowUrl }
    String getVersion() { return version }
    String getBodyStr() {return bodyStr}
}
