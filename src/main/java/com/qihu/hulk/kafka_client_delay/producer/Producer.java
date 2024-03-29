package com.qihu.hulk.kafka_client_delay.producer;

import com.alibaba.fastjson.JSON;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.qihu.hulk.kafka_client_delay.message.Message;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.IntegerSerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Properties;
import java.util.concurrent.*;

/**
 * @author qiufeng
 */
@Component
public class Producer {
    private static final Logger logger = LoggerFactory.getLogger(Producer.class);
    private KafkaProducer<Integer, String> producer;

    @Value("${producer.server}")
    private String server;
    @Value("${producer.topic}")
    private String topic;
    @Value("${producer.msg}")
    private String msg;
    @Value("${producer.intervalMs}")
    private Integer intervalMs;
    @Value("${producer.enable}")
    private Boolean enable;

    public void init() {
        Properties props = new Properties();
        //  指定kafka集群地址
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, this.server);
        //  设置序列化的类。 数据传输的过程中需要进行序列化，消费者获取数据需要反序列化
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, IntegerSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producer = new KafkaProducer<>(props);
    }

    @PostConstruct
    public void run() {
        if (!this.enable) {
            return;
        }
        this.init();

        //Common Thread Pool
        ExecutorService pool = new ThreadPoolExecutor(1, 1,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(1),
                new ThreadFactoryBuilder().setNameFormat("Producer-%d").build(),
                new ThreadPoolExecutor.AbortPolicy());

        pool.submit(this::producer);
    }

    private void producer() {
        int messageNo = 1;
        while (true) {
            Message message = new Message(
                    messageNo++,
                    System.currentTimeMillis(),
                    "".equals(this.msg) ? "Message_" + messageNo : this.msg);

            this.producer.send(new ProducerRecord<>(this.topic, messageNo, JSON.toJSONString(message)), new ProducerCallBack(message));
            sleep();
        }
    }

    private void sleep() {
        try {
            Thread.sleep(this.intervalMs);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}

class ProducerCallBack implements Callback {

    private static final Logger logger = LoggerFactory.getLogger(ProducerCallBack.class);

    private final long startTime;
    private final int key;
    private final String message;

    public ProducerCallBack(Message message) {
        this.startTime = message.getStartTime();
        this.key = message.getNo();
        this.message = message.getMsg();
    }

    @Override
    public void onCompletion(RecordMetadata metadata, Exception exception) {
        long elapsedTime = System.currentTimeMillis() - this.startTime;

        if(exception != null) {
            logger.error("有异常: {}", exception.toString());

            // todo: 在这里做一些补偿机制

        }

        if (metadata != null && this.key%5000 == 0) {
            logger.info("message(key: {}) sent to partition({}), offset({}) in {} ms",
                    this.key,
                    metadata.partition(),
                    metadata.offset(),
                    elapsedTime);
        }
    }



}
