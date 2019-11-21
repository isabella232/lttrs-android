-dontobfuscate

# This class is only being referenced from navigation.xml
-keep class rs.ltt.jmap.mua.util.KeywordLabel

# JMAP entities
-keep class rs.ltt.jmap.common.** {*;}

# Logger
-keep class org.slf4j.** {*;}
-keep class ch.qos.** {*;}

-dontwarn javax.mail.Authenticator
-dontwarn com.fasterxml.jackson.**
-dontwarn java.lang.ClassValue
