package trader.api.stats;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import trader.api.ControllerConstants;
import trader.common.util.JsonUtil;
import trader.service.stats.StatsAggregator;
import trader.service.stats.StatsCollector;
import trader.service.stats.StatsItemAggregation;
import trader.service.stats.StatsItemPublishEvent;

/**
 * Statistics RESTful Controller
 */
@RestController
public class StatsController {
    private static final String URI_PREFIX = ControllerConstants.URL_PREFIX+"/stats";
    private static final Logger logger = LoggerFactory.getLogger(StatsController.class);

    @Autowired
    private ApplicationContext appContext;

    private StatsCollector statsCollector;

    private StatsAggregator statsAggregator;

    /**
     * Connect collector to local aggregator
     */
    @PostConstruct
    public void init()
    {
        try {
            statsCollector = appContext.getBean(StatsCollector.class);
        }catch(Throwable t) {}
        try {
            statsAggregator = appContext.getBean(StatsAggregator.class);
        }catch(Throwable t) {}
        if ( statsCollector!=null && statsAggregator!=null) {
            statsCollector.setEndpoint( (List<StatsItemPublishEvent> events)->{ statsAggregator.aggregate(events); });
        }
    }

    @RequestMapping(path=URI_PREFIX+"/add",
            method=RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public void addStatsValue(@RequestBody List<StatsItemValueField> fields)
    {
        if( statsCollector==null ) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        for(StatsItemValueField f:fields) {
            statsCollector.addStatsItemValue(f.getItem(), Double.valueOf(f.getValue()).longValue());
        }
    }

    @RequestMapping(path=URI_PREFIX+"/set",
            method=RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public void setStatsValue(@RequestBody List<StatsItemValueField> fields)
    {
        if( statsCollector==null ) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        for(StatsItemValueField f:fields) {
            statsCollector.setStatsItemValue(f.getItem(), Double.valueOf(f.getValue()).longValue());
        }
    }

    @RequestMapping(path=URI_PREFIX+"/aggregate",
            method=RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public void getStatsValues(@RequestBody List<StatsItemPublishEvent> events)
    {
        if( statsAggregator==null ) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        statsAggregator.aggregate(events);
    }

    @RequestMapping(path=URI_PREFIX+"/get",
            method=RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public List<StatsItemAggregation> getStatsValues()
    {
        if( statsAggregator==null ) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        List<StatsItemAggregation> result = statsAggregator.getAggregatedValues(null);
        return result;
    }

    @RequestMapping(path=URI_PREFIX+"/get/{filter:.+}",
            method=RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public List<StatsItemAggregation> getStatsValues2(@PathVariable(value="filter") String filter)
    {
        if( statsAggregator==null ) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        List<StatsItemAggregation> result = statsAggregator.getAggregatedValues(filter);
        return (result);
    }


    @RequestMapping(path=URI_PREFIX+"/last",
            method=RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public String getStatsLastValues(@RequestParam(name="pretty", required = false)boolean pretty)
    {
        Map<String, Object> result = new TreeMap<>();
        if ( statsAggregator!=null ) {
            List<StatsItemAggregation> items = statsAggregator.getAggregatedValues(null);
            long thisSeconds = Instant.now().getEpochSecond();
            for(StatsItemAggregation item:items) {
                if ( (thisSeconds-item.getLastAggregateTime() ) >= 2*60) {
                    continue;
                }
                result.put(item.getItem().getKey(), number2str(item.getAggregatedValues().get(StatsItemAggregation.KEY_LAST_VALUE).toString()));
            }
        }
        if( statsCollector!=null ) {
            List<StatsItemPublishEvent> sampledEvents = statsCollector.instantSample();
            for(StatsItemPublishEvent itemEvent: sampledEvents) {
                result.put(itemEvent.getItem().getKey(), number2str(itemEvent.getSampleValue()));
            }
        }
        return JsonUtil.json2str(JsonUtil.object2json(result),pretty);
    }

    @RequestMapping(path=URI_PREFIX+"/last/{filter:.+}",
            method=RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public String getStatsLastValues2(@PathVariable(value="filter") String filter, @RequestParam(name="pretty", required = false)boolean pretty)
    {
        Map<String, Object> result = new TreeMap<>();
        long b = System.currentTimeMillis();
        if( statsAggregator!=null ) {
            List<StatsItemAggregation> items = statsAggregator.getAggregatedValues(filter);
            for(StatsItemAggregation item:items) {
                result.put(item.getItem().getKey(), number2str(item.getAggregatedValues().get(StatsItemAggregation.KEY_LAST_VALUE)));
            }
        }
        if ( statsCollector!=null) {
            List<StatsItemPublishEvent> sampledEvents = statsCollector.instantSample();
            for(StatsItemPublishEvent itemEvent: sampledEvents) {
                if ( filter==null || itemEvent.getItem().getKey().indexOf(filter)>=0 ) {
                    result.put(itemEvent.getItem().getKey(), number2str(itemEvent.getSampleValue()));
                }
            }
        }
        long e = System.currentTimeMillis();
        if ( logger.isDebugEnabled()) {
            logger.debug("getLast/"+filter+" returns "+result.size()+" items in "+(e-b)+" ms");
        }
        return JsonUtil.json2str(JsonUtil.object2json(result),pretty);
    }

    private static String number2str(Object num) {
        if ( num instanceof Double ) {
            double dv = ((Double)num).doubleValue();
            long lv = (long)dv;
            if ( (dv-lv)!=0) {
                BigDecimal d = new BigDecimal((Double)num);
                return d.toPlainString();
            }else {
                return ""+lv;
            }
        }
        return num.toString();
    }
}
