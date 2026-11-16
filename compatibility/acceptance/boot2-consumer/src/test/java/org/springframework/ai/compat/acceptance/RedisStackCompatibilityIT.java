/*
 * Copyright 2023-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.compat.acceptance;

import java.util.*;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.JedisPooled;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.*;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.ai.chat.memory.repository.redis.RedisChatMemoryRepository;
import org.springframework.ai.chat.messages.*;
import static org.junit.jupiter.api.Assertions.*;

/** @author 芋道源码 */
class RedisStackCompatibilityIT {
 @Test void vectorSearchAndJsonMemoryRunWithNativeJedisStillPresent() throws Exception {
  assertEquals("1.8",System.getProperty("java.specification.version"));
  int port=Integer.parseInt(Objects.requireNonNull(System.getenv("REDIS_STACK_TEST_PORT")));
  String suffix=UUID.randomUUID().toString().replace("-","");
  try(JedisPooled client=new JedisPooled("127.0.0.1",port)) {
   assertEquals("PONG",client.ping());
   RedisVectorStore store=RedisVectorStore.builder(client,new FixedEmbeddingModel())
    .indexName("vector"+suffix).prefix("vector:"+suffix+":")
    .metadataFields(RedisVectorStore.MetadataField.tag("category"))
    .initializeSchema(true).build();
   store.afterPropertiesSet();
   store.add(Arrays.asList(new Document("one","apple",Collections.singletonMap("category","fruit")),new Document("two","car",Collections.singletonMap("category","vehicle"))));
   List<Document> results=store.similaritySearch(SearchRequest.builder().query("apple").topK(2).similarityThresholdAll().build());
   assertEquals(2,results.size());assertEquals("one",results.get(0).getId());
   assertEquals("fruit",results.get(0).getMetadata().get("category"));
   List<Document> filtered=store.similaritySearch(SearchRequest.builder().query("apple").topK(2).similarityThresholdAll().filterExpression("category == 'vehicle'").build());
   assertEquals(1,filtered.size());assertEquals("two",filtered.get(0).getId());
   store.delete(Collections.singletonList("one"));
   assertEquals(1,store.count());
   store.delete(Collections.singletonList("two"));
   assertEquals(0,store.count());
   RedisChatMemoryRepository memory=RedisChatMemoryRepository.builder().jedisClient(client)
    .indexName("memory"+suffix).keyPrefix("memory:"+suffix+":").initializeSchema(true).ttlSeconds(60).build();
   memory.add("conversation",Arrays.asList(new UserMessage("hello"),new AssistantMessage("world")));
   List<Message> messages=memory.get("conversation");
   assertEquals(2,messages.size());assertEquals("hello",messages.get(0).getText());assertEquals("world",messages.get(1).getText());
   assertEquals(MessageType.USER,messages.get(0).getMessageType());assertEquals(MessageType.ASSISTANT,messages.get(1).getMessageType());
   Set<String> memoryKeys=client.keys("memory:"+suffix+":*");
   assertFalse(memoryKeys.isEmpty());
   assertTrue(memoryKeys.stream().anyMatch(key->client.ttl(key)>0));
   memory.clear("conversation");assertTrue(memory.get("conversation").isEmpty());
   assertNotNull(Class.forName("redis.clients.jedis.BinaryJedis"));
  }
 }
 @Test void semanticCacheRoundTripAndExpiryUseTheIsolatedClient() throws Exception {
  int port=Integer.parseInt(Objects.requireNonNull(System.getenv("REDIS_STACK_TEST_PORT")));
  String suffix=UUID.randomUUID().toString().replace("-","");
  try(JedisPooled client=new JedisPooled("127.0.0.1",port)) {
   String prefix="cache:"+suffix+":";
   org.springframework.ai.vectorstore.redis.cache.semantic.DefaultSemanticCache cache=
    org.springframework.ai.vectorstore.redis.cache.semantic.DefaultSemanticCache.builder()
     .jedisClient(client).embeddingModel(new FixedEmbeddingModel()).indexName("cache"+suffix).prefix(prefix).build();
   org.springframework.ai.chat.model.ChatResponse response=new org.springframework.ai.chat.model.ChatResponse(
    Collections.singletonList(new org.springframework.ai.chat.model.Generation(new AssistantMessage("cached answer"))));
   cache.set("apple",response,java.time.Duration.ofSeconds(60));
   assertEquals("cached answer",cache.get("apple").get().getResult().getOutput().getText());
   Set<String> keys=client.keys(prefix+"*");assertEquals(1,keys.size());
   String key=keys.iterator().next();assertTrue(client.ttl(key)>0);
   client.pexpire(key,1);
   long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
   while(client.exists(key)&&System.nanoTime()<deadline)Thread.sleep(10);
   assertFalse(client.exists(key));assertFalse(cache.get("apple").isPresent());
   cache.set("apple",response,"context-one");
   assertTrue(cache.get("apple","context-one").isPresent());
   assertFalse(cache.get("apple","context-two").isPresent());
   cache.clear();assertFalse(cache.get("apple").isPresent());
  }
 }
 /** @author 芋道源码 */
 static final class FixedEmbeddingModel implements EmbeddingModel {
  public int dimensions(){return 3;}
  public float[] embed(Document document){return vector(document.getText());}
  public EmbeddingResponse call(EmbeddingRequest request){
   List<Embedding> values=new ArrayList<>();
   for(String text:request.getInstructions())values.add(new Embedding(vector(text),values.size()));
   return new EmbeddingResponse(values);
  }
  private float[] vector(String text){return text.contains("apple")?new float[]{1,0,0}:new float[]{0,1,0};}
 }
}
