import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.neo4j.cypherdsl.support.schema_name.SchemaNames;
/** @author 芋道源码 */
class SchemaNamesOracle {
 public static void main(String[] args) throws Exception {
  List<String> result=new ArrayList<>();
  for(String encoded:Files.readAllLines(Path.of(args[0]))) {
   String value=encoded.equals("~")?null:new String(Base64.getDecoder().decode(encoded),StandardCharsets.UTF_8);
   for(boolean quotes:new boolean[]{false,true})for(int[] version:new int[][]{{-1,-1},{3,5},{4,1},{4,2},{5,1},{5,2},{6,0}}) {
    Optional<String> answer=SchemaNames.sanitize(value,quotes,version[0],version[1]);
    result.add(encoded+"\t"+quotes+"\t"+version[0]+"\t"+version[1]+"\t"+answer.map(s->Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8))).orElse("~"));
   }
  }
  Files.write(Path.of(args[1]),result);System.out.println(result.size()+" official Java 17 sanitizer results");
 }
}
