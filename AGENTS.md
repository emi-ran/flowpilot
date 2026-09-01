# FlowPilot Agent Rules

- Subagentlar Gradle compile, test, build, APK install veya cihaz launch komutu çalıştırmaz.
- Ana ajan Gradle build/test doğrulamasını çalıştırır.
- Ana ajan build başarılıysa debug APK'yı bağlı hedef cihaza yükler ve `com.flowpilot.app` uygulamasını açar.
