# Strimizi OAuth 2 Authentication and Authorization with Keycloak

## Enviroment 

* Openshift Container Platform 4.14
* AMQ Streams 2.6, based on Strimizi 0.36.0
* Red Hat Single Sign-On 7.6, based on Keycloak 18.0.11

## Some assumptions

* This guide will import the Strimizi example `kafka-authz` realm to Keycloak, which can be download at https://github.com/strimzi/strimzi-kafka-oauth/blob/main/examples/docker/keycloak/realms/kafka-authz-realm.json
* This guide requires some Openshift knownledge. 

## Instalation

### Openshift pre-configuration

* Create a new *strimizi-oauth-example* Openshift project

```bash
    oc new-project strimizi-oauth-example
```
### Install Keycloak using Operator

* On the Openshift Web Console, open the Operator Hub and search for Red Hat Single Sign-On Operator and install the operator on the project;
* Create a Keycloak instance using the provided keycloak.yaml file:
```bash
    oc apply -f keycloak.yaml
```
* Login to the Keycloak Administrative Console - the username and password can be found on the credential-keycloak secret;
* Import the file kafka-authz-realm.json to a new realm by clicking in the Add realm button.
* Create the oauth-server-cert secret, this secret will be used by Kafka to properly connect to Keycloak:
    * First, get the tls.crt out of the secret sso-x509-https-secret
    ```bash
    oc get secret sso-x509-https-secret -o yaml | grep tls.crt | awk '{print $2}' | base64 --decode > sso.pem
    ```
    
    * **Don't forget** that only the top CA is required in the certificate chain, so manually edit the sso.pem, leave only the bottom certificate and save it as sso.crt.
    * Create the oauth-server-cert secret:
```bash
    oc create secret generic oauth-server-cert --from-file=sso.crt
```
### Install Kafka using Operator

* On the Openshift Web Console, open the Operator Hub search for AMQ Streams and install the operator on the project;
* Create a Kafka instance using the provided kafka.yaml file:
```bash
    oc apply -f kafka.yaml
```
* Things to notice in kafka.yaml file:
    - Keycloak URL is hardcoded to `keycloak.strimizi-oauth-example.svc`. Change it if using a different setup.
    - Both authentication and authorization has a tlsTrustedCertificates config that point to the `oauth-server-cert` secret created before.
    - The name of the Kafka resource is `my-cluster`. **Don't change this name!** The example `kafka-authz` imported in Keycloak has policies configuration that expect this name of the cluster. 

## Running the clients

### Client preconfiguration

* Just like the Kafka broker needs the Keycloak CA to access the autherization and authentication service, the client applicaion needs both Keycloack and Kafka CA to authenticate and authorize the client. 
    * The keycloak CA is already saved in the sso.crt from previous steps, now extract the Kafka CA from the `my-cluster-cluster-ca-cert` secret:
```bash
    oc get secret my-cluster-cluster-ca-cert -o yaml | grep ca.crt | awk '{print $2}' | base64 --decode > kafka.crt
```
    * Create a truststore and import both Kafka and Keycloak certificates: 

```bash
    export PASSWORD=truststorepassword
    keytool -keystore kafka-client-truststore.p12 -storetype PKCS12 -alias sso   -storepass $PASSWORD -keypass $PASSWORD -import -file sso.crt -noprompt  
    keytool -keystore kafka-client-truststore.p12 -storetype PKCS12 -alias kafka -storepass $PASSWORD -keypass $PASSWORD -import -file kafka.crt -noprompt
```
    * Finally, create a secret with the truststore
    ```bash
    kubectl create secret generic kafka-client-truststore --from-file=./kafka-client-truststore.p12
    ```
* Create the client configuration files:
    * For each client, it is required a configuration file. Here is a basic configuration file template for the clients using our example:

```console
security.protocol=SASL_SSL
ssl.truststore.location=/opt/kafka/certificates/kafka-client-truststore.p12
ssl.truststore.password=truststorepassword
ssl.truststore.type=PKCS12
sasl.mechanism=OAUTHBEARER
sasl.jaas.config=org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required \
  oauth.client.id="${Change to the client ID}" \
  oauth.client.secret="${Change to the client secret}" \
  oauth.ssl.truststore.location="/opt/kafka/certificates/kafka-client-truststore.p12" \
  oauth.ssl.truststore.password="truststorepassword" \
  oauth.ssl.truststore.type="PKCS12" \
  oauth.token.endpoint.uri="https://keycloak.angelo-kafka-oauth-example.svc/auth/realms/kafka-authz/protocol/openid-connect/token" ;
sasl.login.callback.handler.class=io.strimzi.kafka.oauth.client.JaasClientOauthLoginCallbackHandler
```

* In the project folder, there are two examples client configuration files, the `team-a-client.properties` and `team-b-client.properties`. These files are configured using pre-configured clients on the `kafka-authz` Keycloak example realm.

### Running the client

* Use the Pod definition on file `kafka-client-shell.yaml` to run the clients. First, start the pod:
```bash
    oc apply -f kafka-client-shell.yaml
```
* Copy the configuration files to `/tmp` pod folder.:
```bash
    oc cp team-a-client.properties kafka-client-shell:/tmp
    oc cp team-b-client.properties kafka-client-shell:/tmp
```
* Enter in the pod and run the producer:
```bash
    oc rsh  kafka-client-shell /bin/bash
    bin/kafka-console-producer.sh --bootstrap-server my-cluster-kafka-bootstrap:9093 --topic my-topic --producer.config=/tmp/team-a-client.properties
```
* Enter in the pod in a new terminal and run the consumer:
```bash
    oc rsh  kafka-client-shell /bin/bash
    bin/kafka-console-consumer.sh --bootstrap-server kafka-kafka-bootstrap:9093 --topic a_messages   --from-beginning --consumer.config /tmp/team-a-client.properties --group a_consumer_group_a
```



