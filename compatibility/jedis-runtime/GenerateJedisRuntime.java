import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.*;
import java.util.stream.Collectors;
import org.apache.maven.plugins.shade.DefaultShader;
import org.apache.maven.plugins.shade.ShadeRequest;
import org.apache.maven.plugins.shade.filter.SimpleFilter;
import org.apache.maven.plugins.shade.relocation.Relocator;
import org.apache.maven.plugins.shade.relocation.SimpleRelocator;
import org.apache.maven.plugins.shade.resource.ServicesResourceTransformer;

/**
 * Isolates the Redis Stack client without replacing Boot 2's Jedis classes.
 * Runs on the build JVM; it is not part of the runtime artifact.
 *
 * @author 芋道源码
 */
public final class GenerateJedisRuntime {
 public static void main(String[] args) throws Exception {
  Path input=Paths.get(args[0]),output=Paths.get(args[1]);
  Properties hashes=new Properties();try(InputStream stream=Files.newInputStream(Paths.get(args[2]))){hashes.load(stream);}
  Set<File> jars;
  try(java.util.stream.Stream<Path> files=Files.list(input)){jars=files.filter(p->p.toString().endsWith(".jar")).sorted().map(Path::toFile).collect(Collectors.toCollection(LinkedHashSet::new));}
  if(jars.size()!=hashes.size())throw new IllegalStateException("Unexpected runtime dependency set: "+jars);
  for(File file:jars) {
   String expected=hashes.getProperty(file.getName());String actual=hex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file.toPath())));
   if(!actual.equals(expected))throw new IllegalStateException("Unpinned upstream jar: "+file.getName());
   try(JarFile jar=new JarFile(file)) {
    Enumeration<JarEntry> entries=jar.entries();
    while(entries.hasMoreElements()) {
     JarEntry entry=entries.nextElement();String name=entry.getName();
     if(name.endsWith(".class")&&!name.startsWith("META-INF/versions/")&&!name.endsWith("module-info.class")) {
      try(DataInputStream data=new DataInputStream(jar.getInputStream(entry))){data.readInt();data.readUnsignedShort();int major=data.readUnsignedShort();if(major>52)throw new IllegalStateException("Java8-incompatible dependency: "+file+"!"+name+" ("+major+")");}
     }
    }
   }
  }
  Path manifest=output.getParent().resolve("jedis-generated-files.txt");
  if(Files.exists(manifest)) for(String entry:Files.readAllLines(manifest)) {
   Path previous=output.resolve(entry).normalize();
   if(!previous.startsWith(output.normalize()))throw new IOException("Invalid generated manifest entry");
   Files.deleteIfExists(previous);
  }
  List<String> generated=new ArrayList<>();
  Path shaded=output.getParent().resolve("relocated-jedis.jar");Files.createDirectories(output);
  ShadeRequest request=new ShadeRequest();request.setJars(jars);request.setUberJar(shaded.toFile());
  request.setFilters(Collections.singletonList(new SimpleFilter(jars,Collections.emptySet(),new HashSet<>(Arrays.asList("module-info.class","META-INF/versions/**","META-INF/*.SF","META-INF/*.RSA","META-INF/*.DSA")))));
  String prefix="org.springframework.ai.compat.jedis.";
  List<Relocator> relocators=new ArrayList<>();
  relocators.add(new SimpleRelocator("redis.clients.jedis",prefix+"internal",null,Arrays.asList("redis.clients.jedis.search.**","redis.clients.jedis.json.**")));
  for(String[] pair:new String[][]{{"org.apache.commons.pool2","pool"},{"org.json","json"},{"com.google.gson","gson"},{"com.google.errorprone.annotations","annotations"},{"redis.clients.authentication","authentication"}})relocators.add(new SimpleRelocator(pair[0],prefix+pair[1],null,null));
  request.setRelocators(relocators);request.setResourceTransformers(Collections.singletonList(new ServicesResourceTransformer()));
  new DefaultShader().shade(request);
  try(JarFile jar=new JarFile(shaded.toFile())) {
   Enumeration<JarEntry> entries=jar.entries();
   while(entries.hasMoreElements()) {
    JarEntry entry=entries.nextElement();if(entry.isDirectory()||entry.getName().equals("META-INF/MANIFEST.MF"))continue;
    Path target=output.resolve(entry.getName()).normalize();if(!target.startsWith(output.normalize()))throw new IOException("Invalid shaded entry");
    Files.createDirectories(target.getParent());try(InputStream stream=jar.getInputStream(entry)){Files.copy(stream,target,StandardCopyOption.REPLACE_EXISTING);generated.add(output.relativize(target).toString());}
   }
  }
  // Preserve each upstream notice under a unique path, avoiding first-jar wins.
  for(File file:jars)try(JarFile jar=new JarFile(file)) {
   Enumeration<JarEntry> entries=jar.entries();
   while(entries.hasMoreElements()) {
    JarEntry entry=entries.nextElement();String name=entry.getName();String upper=name.toUpperCase(Locale.ROOT);
    if(!entry.isDirectory()&&(upper.contains("LICENSE")||upper.contains("NOTICE"))) {
     Path target=output.resolve("META-INF/yudao-jedis-upstream/"+file.getName()+"/"+name).normalize();if(!target.startsWith(output.normalize()))throw new IOException("Invalid notice entry");
     Files.createDirectories(target.getParent());try(InputStream stream=jar.getInputStream(entry)){Files.copy(stream,target,StandardCopyOption.REPLACE_EXISTING);generated.add(output.relativize(target).toString());}
    }
   }
  }
  Files.write(manifest,generated);
 }
 private static String hex(byte[] bytes){StringBuilder value=new StringBuilder();for(byte part:bytes)value.append(String.format(Locale.ROOT,"%02x",part&255));return value.toString();}
}
