# Strimzi Mirror Maker Example

## Enviroment 

* Openshift Container Platform 4.17
* AMQ Streams 2.8, based on Strimizi 0.43.0

## Instalation

1. Create the source and target projects

```sh
oc new-project strimzi-mm-example-source
oc new-project strimzi-mm-example-target
```

2. Install the operator

```sh
oc create -f 001-subscription-source.yaml -n strimzi-mm-example-source
oc create -f 002-subscription-target.yaml -n strimzi-mm-example-target
```

3. Create the Kafka cluster

```sh
oc create -f 003-kafka-source.yaml -n strimzi-mm-example-source
oc create -f 004-kafka-target.yaml -n strimzi-mm-example-target
```

4. Create the Kafka Topic (Create the Kafka topic in the target is not required)

```sh
oc create -f 005-kafka-topic.yaml -n strimzi-mm-example-source
oc create -f 005-kafka-topic.yaml -n strimzi-mm-example-target
```

5. Create the Mirror Maker 2 cluster
```sh
oc create -f 006-kafka-mirrormaker2.yaml -n strimzi-mm-example-target
```

## Use case 01: Messages and consumer groups moving from the source to the target

1. Let's populate the source cluster with some data

```sh
oc rsh -n strimzi-mm-example-source kafka-cluster-kafka-0
for i in {0..1000}; do echo "This is a test message!" >> /tmp/message < /tmp/message; done
bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic example.topic < /tmp/message
```

2. Leave some consumer groups open to test (Optional)
```sh
oc rsh -n strimzi-mm-example-source kafka-cluster-kafka-0
bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic example.topic --group test-group --from-beginning
```

## Useful Kafka commands

1. Consume messages from a topic
```sh 
bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic example.topic --from-beginning
```

2. List the topics on the cluster
```sh 
bin/kafka-topics.sh --list --bootstrap-server localhost:9092
```

3. List the consumer groups
```sh 
bin/kafka-consumer-groups.sh --list --bootstrap-server localhost:9092
```

4. Get details of the consumer group 
```sh 
bin/kafka-consumer-groups.sh --describe --bootstrap-server localhost:9092  --group <name>
```

5. Delete the consumer group 
```sh 
bin/kafka-consumer-groups.sh --delete --bootstrap-server localhost:9092  --group <name>
```

6. Monitore details of the consumer group 
```sh 
watch oc exec -n strimzi-mm-example-target kafka-cluster-kafka-0 -- bin/kafka-consumer-groups.sh --describe --bootstrap-server localhost:9092  --group <name>
```

7. Create a topic
```sh 
bin/kafka-topics.sh --bootstrap-server localhost:9092 --topic test.replication.1.topic --create --partitions 1 --replication-factor 1
```

8. Produce 100 messages to the topic 
```sh 
bin/kafka-producer-perf-test.sh --topic test.replication.1.topic --throughput 1 --num-records 100 --record-size 64 --producer-props acks=1  --print-metrics --producer-props bootstrap.servers=localhost:9092
```

9. Consume 10 messages to the topic
```sh 
bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic test.replication.1.topic --group group.1 --from-beginning --max-messages 10
```

9. Consume 10 more messages
```sh 
bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic test.replication.1.topic --group group.1  --max-messages 10
```