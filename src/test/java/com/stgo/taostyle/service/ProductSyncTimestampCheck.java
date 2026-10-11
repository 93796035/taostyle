package com.stgo.taostyle.service;
import java.lang.reflect.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import javax.persistence.*;
import org.json.JSONObject;
public final class ProductSyncTimestampCheck {
 static final String URL="jdbc:mysql://127.0.0.1:13317/product_sync_test?connectionTimeZone=SERVER&preserveInstants=true&allowPublicKeyRetrieval=true&useSSL=false";
 static boolean hideLookup;
 public static void main(String[] args)throws Exception{
  try(Connection db=DriverManager.getConnection(URL.replace("/product_sync_test?","/?"),"root","")){db.createStatement().execute("DROP DATABASE IF EXISTS product_sync_test");db.createStatement().execute("CREATE DATABASE product_sync_test");}
  try(Connection db=DriverManager.getConnection(URL,"root","")){
   StringBuilder ddl=new StringBuilder("CREATE TABLE Product(id BIGINT AUTO_INCREMENT PRIMARY KEY,person_id BIGINT,local_id BIGINT,");
   for(String c:new String[]{"subject","content","code","unit","producarea","brand","category","mnemonic"})ddl.append(c).append(" VARCHAR(255),");
   for(String c:new String[]{"folderid","price","cost","qst","index","stock","stock1","stock2","stock3","stock1_shortage","deleted","weekly_sales_qty","monthly_sales_qty"})ddl.append('`').append(c).append("` INTEGER,");
   ddl.append("coverdays DOUBLE,last_modified TIMESTAMP NULL DEFAULT NULL,UNIQUE(person_id,local_id),UNIQUE(person_id,code))");db.createStatement().execute(ddl.toString());
   MobileProductSyncService service=service(db);
   JSONObject data=new JSONObject().put("code","062067382567").put("stock1",9).put("stock2",0).put("last_modified","2026-10-10T16:01:16.123456Z");
   service.upsert(250808L,204L,data);check(db,"2026-10-10T16:01:16.123456Z",9);
   data.put("last_modified","2026-10-10T12:02:21.199-04:00").put("stock1",10);service.upsert(250808L,999L,data);check(db,"2026-10-10T16:02:21.199Z",10);
   hideLookup=true;data.put("last_modified","2026-10-10T16:03:00.987654Z").put("stock1",11);service.upsert(250808L,204L,data);hideLookup=false;check(db,"2026-10-10T16:03:00.987654Z",11);
   for(String bad:new String[]{"","2026-10-10 16:04:00","garbage"}){
    try{service.upsert(250808L,204L,data.put("last_modified",bad));throw new AssertionError("Accepted bad timestamp");}catch(IllegalArgumentException expected){}
    check(db,"2026-10-10T16:03:00.987654Z",11);
   }
   db.createStatement().execute("UPDATE Product SET price=500,cost=350,stock2=7,stock3=12 WHERE code='062067382567'");
   JSONObject pos=new JSONObject().put("last_modified","2026-10-10T16:00:00Z").put("stock1",8)
           .put("stock1_shortage",-4).put("price",400).put("cost",250).put("stock2",99).put("stock3",99);
   service.mergeClientInventory(250808L,"062067382567",pos);
   try(ResultSet r=db.createStatement().executeQuery("SELECT price,cost,stock1,stock2,stock3,stock1_shortage,stock FROM Product WHERE code='062067382567'")){
    if(!r.next()||r.getInt(1)!=500||r.getInt(2)!=350||r.getInt(3)!=8||r.getInt(4)!=7||r.getInt(5)!=12||r.getInt(6)!=-4||r.getInt(7)!=27)
     throw new AssertionError("Client merge lost server details or inventory");
   }
   try{service.mergeClientInventory(250808L,"062067382567",new JSONObject().put("last_modified","2026-10-10T16:00:00Z").put("stock1",0));throw new AssertionError("Missing monque accepted");}catch(IllegalArgumentException expected){}
   for(String zone:new String[]{"UTC","America/Toronto","Asia/Shanghai"}){TimeZone.setDefault(TimeZone.getTimeZone(zone));try(Connection reader=DriverManager.getConnection(URL,"root","")){check(reader,"2026-10-10T16:03:00.987654Z",8);}}

  }
  Class<?> controllerType=Class.forName("com.stgo.taostyle.web.ProductController");
  Object controller=controllerType.getDeclaredConstructor().newInstance();
  Method newer=controllerType.getDeclaredMethod("isClientVersionNewer",String.class,String.class);newer.setAccessible(true);
  if((Boolean)newer.invoke(controller,"2026-10-10T15:02:21.199Z","2026-10-10T16:01:16Z"))throw new AssertionError("Older client accepted");
  if(!(Boolean)newer.invoke(controller,"2026-10-10T16:02:21.199Z","2026-10-10T16:01:16Z"))throw new AssertionError("Newer client rejected");
  if((Boolean)newer.invoke(controller,"2026-10-10T12:01:16-04:00","2026-10-10T16:01:16Z"))throw new AssertionError("Same instant accepted as newer");
  if((Boolean)newer.invoke(controller,"2026-10-10T16:01:16.999Z","2026-10-10T16:01:16.001Z"))throw new AssertionError("Same second accepted as newer");
  System.out.println("PASS: insert/update/duplicate use server acceptance time, UTC-7 database, UTC/Toronto/Shanghai readers, second precision, invalid time rejection, equal-second rejection, Client stock1/monque merge preserves server price/cost/stock2/stock3.");
 }
 static void check(Connection db,String expected,int stock)throws Exception{
  Class<?> controllerType=Class.forName("com.stgo.taostyle.web.ProductController");
  Method format=controllerType.getDeclaredMethod("syncTimestamp",Object.class);format.setAccessible(true);
  String wire=(String)format.invoke(controllerType.getDeclaredConstructor().newInstance(),Timestamp.from(Instant.parse(expected).truncatedTo(java.time.temporal.ChronoUnit.SECONDS)));
  if(!Instant.parse(wire).equals(Instant.parse(expected).truncatedTo(java.time.temporal.ChronoUnit.SECONDS)))throw new AssertionError("API changed timestamp precision");
  try(ResultSet r=db.createStatement().executeQuery("SELECT last_modified,stock1,UNIX_TIMESTAMP(last_modified),UNIX_TIMESTAMP(CURRENT_TIMESTAMP(0)) FROM Product WHERE code='062067382567'")){if(!r.next()||(!r.getTimestamp(1).toInstant().equals(Instant.ofEpochSecond(r.getLong(3))) || r.getLong(4)-r.getLong(3) < 0 || r.getLong(4)-r.getLong(3) > 5)||r.getInt(2)!=stock)throw new AssertionError("Incorrect server acceptance timestamp/stock: "+expected);}
 }
 static MobileProductSyncService service(Connection db)throws Exception{
  EntityManager em=(EntityManager)Proxy.newProxyInstance(EntityManager.class.getClassLoader(),new Class[]{EntityManager.class},(proxy,m,args)->{
   if(!m.getName().equals("createNativeQuery"))throw new UnsupportedOperationException(m.getName());String sql=(String)args[0];Map<Integer,Object> params=new TreeMap<>();
   return Proxy.newProxyInstance(Query.class.getClassLoader(),new Class[]{Query.class},(q,method,a)->{
    String name=method.getName();if(name.equals("setParameter")){params.put((Integer)a[0],a[1]);return q;}if(hideLookup&&name.equals("getResultList"))return Collections.emptyList();
    try(PreparedStatement ps=db.prepareStatement(sql)){for(Map.Entry<Integer,Object> e:params.entrySet())ps.setObject(e.getKey(),e.getValue());if(name.equals("executeUpdate"))return ps.executeUpdate();
     try(ResultSet rs=ps.executeQuery()){List<Object> results=new ArrayList<>();while(rs.next())results.add(rs.getObject(1));if(name.equals("getSingleResult"))return results.get(0);if(name.equals("getResultList"))return results;}}
    throw new UnsupportedOperationException(name);
   });
  });
  MobileProductSyncService service=new MobileProductSyncService();Field f=MobileProductSyncService.class.getDeclaredField("entityManager");f.setAccessible(true);f.set(service,em);return service;
 }
}
