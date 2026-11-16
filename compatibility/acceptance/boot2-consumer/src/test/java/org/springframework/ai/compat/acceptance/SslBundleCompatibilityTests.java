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

import java.security.KeyStore;
import java.io.InputStream;
import java.util.*;
import javax.net.ssl.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ssl.*;
import static org.junit.jupiter.api.Assertions.*;

/** @author 芋道源码 */
class SslBundleCompatibilityTests {
 @Test void bundleCreatesManagersWithKeyAliasAndTrustStoreOnJava8() throws Exception {
  assertEquals("1.8",System.getProperty("java.specification.version"));
  KeyStore store=KeyStore.getInstance("JKS");
  try(InputStream input=getClass().getResourceAsStream("/localhost-test.jks")){assertNotNull(input);store.load(input,"test-only".toCharArray());}
  String alias=store.aliases().nextElement();
  SslBundle bundle=SslBundle.of(SslStoreBundle.of(store,"test-only",store),SslBundleKey.of("test-only",alias),SslOptions.of(null,new String[]{"TLSv1.2"}),"TLSv1.2");
  assertEquals("TLSv1.2",bundle.createSslContext().getProtocol());
  assertArrayEquals(new String[]{"TLSv1.2"},bundle.getOptions().getEnabledProtocols());
  X509ExtendedKeyManager manager=Arrays.stream(bundle.getManagers().getKeyManagers()).filter(X509ExtendedKeyManager.class::isInstance).map(X509ExtendedKeyManager.class::cast).findFirst().get();
  assertEquals(alias,manager.chooseClientAlias(new String[]{"RSA"},null,null));
  assertNotNull(manager.getPrivateKey(alias));assertNotNull(bundle.getManagers().getTrustManagers());
  assertThrows(IllegalStateException.class,()->SslBundle.of(SslStoreBundle.of(store,"test-only",store),SslBundleKey.of("test-only","missing")).getManagers().getKeyManagers());
  assertSame(SSLContext.getDefault(),SslBundle.systemDefault().createSslContext());
 }
 @Test void registryRejectsMissingAndDuplicateNamesAndNotifiesUpdates() {
  DefaultSslBundleRegistry registry=new DefaultSslBundleRegistry();
  List<String> registrations=new ArrayList<>();registry.addBundleRegisterHandler((name,bundle)->registrations.add(name));
  SslBundle original=SslBundle.of(null);registry.registerBundle("one",original);
  assertSame(original,registry.getBundle("one"));assertEquals(Collections.singletonList("one"),registrations);
  assertThrows(IllegalStateException.class,()->registry.registerBundle("one",original));
  assertThrows(NoSuchSslBundleException.class,()->registry.getBundle("missing"));
  List<SslBundle> updates=new ArrayList<>();registry.addBundleUpdateHandler("one",updates::add);
  SslBundle replacement=SslBundle.of(null,null,null,"TLSv1.2");registry.updateBundle("one",replacement);
  assertSame(replacement,registry.getBundle("one"));assertEquals(Collections.singletonList(replacement),updates);
 }
}
