package service.rule

import cn.xnatural.enet.event.EL
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import core.OkHttpSrv
import core.ServerTpl
import core.Utils
import core.jpa.BaseRepo
import dao.entity.DataCollector
import dao.entity.FieldType
import dao.entity.RuleField
import groovy.text.GStringTemplateEngine
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer

import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function

/**
 * 属性管理
 * 属性别名
 * 属性值函数
 */
class AttrManager extends ServerTpl {

    @Lazy def                                                      repo             = bean(BaseRepo, 'jpa_rule_repo')
    /**
     * RuleField(enName, cnName), RuleField
     */
    final Map<String, RuleField>                                   attrMap          = new ConcurrentHashMap<>(1000)
    /**
     * 数据获取函数. 函数名 -> 函数
     */
    protected final Map<String, Function<DecisionContext, Object>> dataCollectorMap = new ConcurrentHashMap()


    @EL(name = 'jpa_rule.started', async = true)
    void init() {
        // 有顺序
        loadField()
        loadDataCollector()
        ep.fire("${name}.started")
    }


    /**
     * 执行数据收集, 获取属性值
     * @param aName 属性名
     * @param ctx
     * @return 当前属性的值
     */
    def dataCollect(String aName, DecisionContext ctx) {
        def field = attrMap.get(aName)
        if (field == null) {
            log.warn("未找到属性'$aName'对应的配置")
            return
        }
        String collectorName = field.dataCollector // 属性对应的 值 收集器名
        if (!collectorName) {
            log.warn(ctx.logPrefix() + "属性'" + aName + "'没有对应的取值配置")
            return null
        }
        if (ctx.dataCollectResult.containsKey(collectorName)) { // 已查询过
            return ctx.dataCollectResult.get(collectorName).get(aName)
        }

        // 函数执行
        def doApply = {Function<DecisionContext, Object> fn ->
            log.debug(ctx.logPrefix() + "Get attr '{}' value apply function: '{}'", aName, collectorName)
            def v = null
            try {
                v = fn.apply(ctx)
            } catch (ex) { // 接口执行报错, 默认继续往下执行规则
                log.error(ctx.logPrefix() + "数据收集器'$collectorName' 执行错误".toString(), ex)
            }
            if (v instanceof Map) { // 收集器,收集结果为多个属性的值, 则暂先保存
                Map<String, Object> result = new HashMap<>()
                ctx.dataCollectResult.put(collectorName, result)
                v.each {entry ->
                    String k = (String) entry.key
                    result.put(k, entry.value)
                    k = alias(k)
                    if (k) result.put(k, entry.value)
                }
                return result.get(aName)
            }
            else return v
        }

        def fn = dataCollectorMap.get(collectorName)
        if (fn) {
            return doApply(fn)
        } else {
            initDataCollect( // 重新去数据库中查找
                repo.find(DataCollector) {root, query, cb -> cb.equal(root.get('enName'), collectorName)}
            )
            fn = dataCollectorMap.get(collectorName)
            if (fn) return doApply(fn)
            else {
                log.warn(ctx.logPrefix() + "Not fund attr '{}' mapped getter function '{}'", aName, collectorName)
                return null
            }
        }
    }


    /**
     * 决策产生的数据接口调用
     * @param ctx 当前决策 DecisionContext
     * @param collector 数据配置
     * @param spend 接口调用花费时间
     * @param url url地址
     * @param bodyStr 请求body
     * @param resultStr 接口返回结果
     * @param resolveResult 解析结果
     */
    @EL(name = 'decision.dataCollect')
    void dataCollected(DecisionContext ctx, DataCollector collector, Long spend, String url, String bodyStr, String resultStr, Map resolveResult) {

    }


    /**
     * 数据收集器 设置
     * @param collectorName 收集器名
     * @param collector 收集器函数
     */
    Function<DecisionContext, Object> setCollector(String collectorName, Function<DecisionContext, Object> collector) { dataCollectorMap.put(collectorName, collector) }


    /**
     * 得到属性对应的别名
     * @param aName 属性名
     * @return null: 没有别名
     */
    String alias(String aName) {
        def record = attrMap.get(aName)
        if (record == null) return null
        else if (record.cnName == aName) return record.enName
        else if (record.enName == aName) return record.cnName
        null
    }


    /**
     * 属性值类型转换
     * @param aName 属性名
     * @param aValue 属性值
     * @return 转换后的值
     */
    Object convert(String aName, Object aValue) {
        if (aValue == null) return aValue
        def field = attrMap.get(aName)
        if (field == null) return aValue
        Utils.to(aValue, field.type.clzType)
    }


    /**
     * 加载属性
     */
    void loadField() {
        initAttr()
        log.info("加载属性配置")
        int page = 1
        do {
            def p = repo.findPage(RuleField, (page++), 100)
            if (!p.list) break
            p.list.each {field ->
                attrMap.put(field.enName, field)
                attrMap.put(field.cnName, field)
            }

        } while (true)
    }


    // ======================= 监听变化 ==========================
    @EL(name = ['updateField', 'addField'], async = true)
    void listenFieldChange(String enName) {
        def field = repo.find(RuleField) {root, query, cb -> cb.equal(root.get('enName'), enName)}
        attrMap.put(field.enName, field)
        attrMap.put(field.cnName, field)
        ep.fire('remote', app.name, 'updateField', [enName])
    }
    @EL(name = 'delField')
    void listenDelField(String enName) {
        def field = attrMap.remove(enName)
        attrMap.remove(field.cnName)
        ep.fire('remote', app.name, 'delField', [enName])
    }
    @EL(name = 'addDataCollector', async = true)
    void listenAddDataCollector(String enName) {
        def collector = repo.find(DataCollector) {root, query, cb -> cb.equal(root.get('enName'), enName)}
        initDataCollect(collector)
        ep.fire('remote', app.name, 'addDataCollector', [enName])
    }
    @EL(name = 'updateDataCollector')
    void listenUpdateDataCollector(String enName) {
        def collector = repo.find(DataCollector) {root, query, cb -> cb.equal(root.get('enName'), enName)}
        initDataCollect(collector)
        loadField()
        ep.fire('remote', app.name, 'updateDataCollector', [enName])
    }
    @EL(name = 'delDataCollector')
    void listenDelCollector(String enName) {
        dataCollectorMap.remove(enName)
        ep.fire('remote', app.name, 'delDataCollector', [enName])
    }



    /**
     * 初始化默认属性集
     */
    protected void initAttr() {
        if (repo.count(RuleField) == 0) {
            log.info("初始化默认属性集")
            repo.saveOrUpdate(new RuleField(enName: 'idNumber', cnName: '身份证号码', type: FieldType.Str))
            repo.saveOrUpdate(new RuleField(enName: 'name', cnName: '姓名', type: FieldType.Str))
            repo.saveOrUpdate(new RuleField(enName: 'mobileNo', cnName: '手机号码', type: FieldType.Str))
            repo.saveOrUpdate(new RuleField(enName: 'age', cnName: '年龄', type: FieldType.Int, dataCollector: 'age'))
            repo.saveOrUpdate(new RuleField(enName: 'gender', cnName: '性别', type: FieldType.Str, dataCollector: 'gender', comment: '值: F,M'))
            repo.saveOrUpdate(new RuleField(enName: 'week', cnName: '星期几', type: FieldType.Int, dataCollector: 'week', comment: '值: 1,2,3,4,5,6,7'))
        }
    }


    /**
     * 加载数据集成
     */
    void loadDataCollector() {
        initDataCollector()
        log.info("加载数据集成配置")
        repo.findList(DataCollector).each {initDataCollect(it)}
    }


    /**
     * 初始化默认数据集
     */
    protected void initDataCollector() {
        if (repo.count(DataCollector) == 0) {
            log.info("初始化默认数据集")
            repo.saveOrUpdate(new DataCollector(type: 'script', enName: 'week', cnName: '星期几', comment: '值: 1,2,3,4,5,6,7', computeScript: """
Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1
            """.trim()))
            repo.saveOrUpdate(new DataCollector(type: 'script', enName: 'gender', cnName: '性别', comment: '根据身份证计算. 值: F,M', computeScript: """
if (idNumber && idNumber.length() > 17) {
    Integer.parseInt(idNumber.substring(16, 17)) % 2 == 0 ? 'F' : 'M'
} else null
            """.trim()))
            repo.saveOrUpdate(new DataCollector(type: 'script', enName: 'age', cnName: '年龄', comment: '根据身份证计算', computeScript: """
if (idNumber && idNumber.length() > 17) {
    Calendar cal = Calendar.getInstance()
    int yearNow = cal.get(Calendar.YEAR)
    int monthNow = cal.get(Calendar.MONTH)+1
    int dayNow = cal.get(Calendar.DATE)

    int birthday = Integer.valueOf(idNumber.substring(6, 14))
    int year = Integer.valueOf(idNumber.substring(6, 10))
    int month = Integer.valueOf(idNumber.substring(10,12))
    int day = Integer.valueOf(idNumber.substring(12,14))
    
    if ((month < monthNow) || (month == monthNow && day <= dayNow)){
        yearNow - year
    } else {
        yearNow - year - 1
    }
} else null
            """.trim()))
        }
    }


    /**
     * 初始全数据收集器
     * @param record
     */
    void initDataCollect(DataCollector record) {
        if (!record) return
        if ('http' == record.type) { // http 接口
            def http = bean(OkHttpSrv)
            Closure parseFn
            if (record.parseScript) {
                Binding binding = new Binding()
                def config = new CompilerConfiguration()
                def icz = new ImportCustomizer()
                config.addCompilationCustomizers(icz)
                icz.addImports(JSON.class.name, JSONObject.class.name)
                parseFn = new GroovyShell(Thread.currentThread().contextClassLoader, binding, config).evaluate("$record.parseScript")
            }
            // 变量替换
            def tplEngine = new GStringTemplateEngine(Thread.currentThread().contextClassLoader)
            setCollector(record.enName) { ctx -> // 数据集成中3方接口访问过程
                String result
                String url = tplEngine.createTemplate(record.url).make(new HashMap(1) {
                    @Override
                    Object get(Object key) {
                        def v = ctx.data.get(key)
                        return v == null ? '' : URLEncoder.encode(v.toString(), 'utf-8')
                    }
                }).toString()
                String bodyStr = record.bodyStr ? tplEngine.createTemplate(record.bodyStr).make(new HashMap(1) {
                    @Override
                    Object get(Object key) {
                        def v = ctx.data.get(key)
                        return v == null ? '' : URLEncoder.encode(v.toString(), 'utf-8')
                    }
                }).toString() : ''
                Object resolveResult
                if ('get'.equalsIgnoreCase(record.method)) {
                    result = http.get(url).execute()
                } else if ('post'.equalsIgnoreCase(record.method)) {
                    result = http.post(url).textBody(bodyStr).contentType(record.contentType).execute()
                } else throw new Exception("Not support http method $record.method")
                String logMsg = ctx.logPrefix() + "接口调用: name: $record.enName, url: $url, bodyStr: $bodyStr, result: $result"
                if (parseFn) {
                    try {
                        resolveResult = parseFn.rehydrate(ctx.data, parseFn, this)(result)
                    } catch (ex) {
                        throw new RuntimeException("解析函数'$record.enName'执行失败", ex)
                    } finally {
                        log.info(logMsg + ", parseResult: " + resolveResult)
                    }
                    return resolveResult
                }
                log.info(logMsg)
                return result
            }
        } else if ('script' == record.type) {
            if (record.computeScript) {
                Binding binding = new Binding()
                def config = new CompilerConfiguration()
                def icz = new ImportCustomizer()
                config.addCompilationCustomizers(icz)
                icz.addImports(JSON.class.name, JSONObject.class.name)
                Closure script = new GroovyShell(Thread.currentThread().contextClassLoader, binding, config).evaluate("{ -> $record.computeScript}")
                setCollector(record.enName) { ctx ->
                    Object result
                    try {
                        result = script.rehydrate(ctx.data, script, this)()
                    } catch (ex) {
                        throw new RuntimeException("脚本函数'$record.enName'执行失败", ex)
                    } finally {
                        log.debug(ctx.logPrefix() + "脚本函数'$record.enName'执行结果: $result".toString())
                    }
                    return result
                }
            }
        } else throw new Exception("Not support type: $record.type")
    }
}
