#include <WiFi.h>
#include <HTTPSServer.hpp>
#include <SSLCert.hpp>
#include <HTTPRequest.hpp>
#include <HTTPResponse.hpp>
#include <LittleFS.h>
#include <ArduinoJson.h>
#include <mbedtls/x509_crt.h>
#include <mbedtls/pk.h>
#include <mbedtls/base64.h>

using namespace httpsserver;

// ====================== VARIÁVEIS ======================
String wifi_ssid = "";
String wifi_password = "";

HTTPSServer* secureServer = nullptr;

// Buffers dos certificados - DEVEM permanecer em memória durante toda a execução!
uint8_t* globalCertDER = nullptr;
size_t globalCertDERLen = 0;
uint8_t* globalKeyDER = nullptr;
size_t globalKeyDERLen = 0;

// ====================== FUNÇÕES AUXILIARES ======================

// Converte certificado PEM para DER usando mbedtls
bool convertPEMtoDER(const uint8_t* pemData, size_t pemLen, uint8_t** derData, size_t* derLen, bool isPrivateKey) {
  mbedtls_x509_crt crt;
  mbedtls_pk_context pk;
  int ret;
  
  if (isPrivateKey) {
    // Converte chave privada
    mbedtls_pk_init(&pk);
    // mbedtls v5.5 requer 7 parâmetros (f_rng e p_rng adicionados)
    ret = mbedtls_pk_parse_key(&pk, pemData, pemLen + 1, NULL, 0, NULL, NULL);
    
    if (ret != 0) {
      Serial.printf("❌ Erro ao fazer parse da chave privada PEM: -0x%04x\n", -ret);
      mbedtls_pk_free(&pk);
      return false;
    }
    
    // Escreve em formato DER
    *derLen = 4096; // Buffer temporário
    *derData = new uint8_t[*derLen];
    
    ret = mbedtls_pk_write_key_der(&pk, *derData, *derLen);
    mbedtls_pk_free(&pk);
    
    if (ret < 0) {
      Serial.printf("❌ Erro ao converter chave para DER: -0x%04x\n", -ret);
      delete[] *derData;
      return false;
    }
    
    // mbedtls_pk_write_key_der escreve do final para o início
    // então precisamos ajustar o ponteiro e o tamanho
    *derLen = ret;
    uint8_t* temp = new uint8_t[*derLen];
    memcpy(temp, *derData + (4096 - ret), *derLen);
    delete[] *derData;
    *derData = temp;
    
  } else {
    // Converte certificado
    mbedtls_x509_crt_init(&crt);
    ret = mbedtls_x509_crt_parse(&crt, pemData, pemLen + 1);
    
    if (ret != 0) {
      Serial.printf("❌ Erro ao fazer parse do certificado PEM: -0x%04x\n", -ret);
      mbedtls_x509_crt_free(&crt);
      return false;
    }
    
    // Copia dados DER do certificado
    *derLen = crt.raw.len;
    *derData = new uint8_t[*derLen];
    memcpy(*derData, crt.raw.p, *derLen);
    
    mbedtls_x509_crt_free(&crt);
  }
  
  Serial.printf("✅ Conversão PEM→DER bem-sucedida: %u bytes → %u bytes\n", pemLen, *derLen);
  return true;
}

// Monta LittleFS (sem formatar)
bool mountLittleFS() {
  if (!LittleFS.begin()) {
    Serial.println("❌ Erro ao montar LittleFS");
    return false;
  }
  return true;
}

// Formata o sistema de arquivos LittleFS (APAGA TODOS OS DADOS!)
bool formatLittleFS() {
  Serial.println("⚠️  ATENÇÃO: Formatando LittleFS... Todos os dados serão apagados!");
  
  if (!LittleFS.format()) {
    Serial.println("❌ Erro ao formatar LittleFS");
    return false;
  }
  
  Serial.println("✅ LittleFS formatado com sucesso!");
  return true;
}

// Lista todos os arquivos no LittleFS
void listFiles() {
  Serial.println("\n📁 Arquivos no LittleFS:");
  File root = LittleFS.open("/");
  File file = root.openNextFile();
  
  if (!file) {
    Serial.println("   (vazio)");
  }
  
  while (file) {
    Serial.print("   - ");
    Serial.print(file.name());
    Serial.print(" (");
    Serial.print(file.size());
    Serial.println(" bytes)");
    file = root.openNextFile();
  }
  Serial.println();
}

// Verifica e mostra informações detalhadas de um arquivo
void checkFile(const char* filename) {
  if (!LittleFS.exists(filename)) {
    Serial.printf("❌ Arquivo '%s' não existe!\n", filename);
    return;
  }

  File file = LittleFS.open(filename, "r");
  if (!file) {
    Serial.printf("❌ Erro ao abrir '%s'\n", filename);
    return;
  }

  size_t fileSize = file.size();
  Serial.printf("\n🔍 Diagnóstico de '%s':\n", filename);
  Serial.printf("   Tamanho: %u bytes\n", fileSize);

  if (fileSize == 0) {
    Serial.println("   ⚠️  Arquivo está vazio!");
    file.close();
    return;
  }

  // Lê primeiros 100 bytes
  uint8_t buffer[100];
  size_t bytesToRead = min((size_t)100, fileSize);
  size_t bytesRead = file.read(buffer, bytesToRead);

  Serial.printf("   Primeiros %u bytes (HEX):\n   ", bytesRead);
  for (size_t i = 0; i < bytesRead && i < 50; i++) {
    Serial.printf("%02X ", buffer[i]);
    if ((i + 1) % 16 == 0) Serial.print("\n   ");
  }
  Serial.println();

  Serial.println("   Primeiros caracteres (ASCII):");
  Serial.print("   ");
  for (size_t i = 0; i < bytesRead && i < 100; i++) {
    if (buffer[i] >= 32 && buffer[i] < 127) {
      Serial.write(buffer[i]);
    } else if (buffer[i] == '\n') {
      Serial.print("\\n");
    } else if (buffer[i] == '\r') {
      Serial.print("\\r");
    } else {
      Serial.print('.');
    }
  }
  Serial.println("\n");

  // Verifica terminadores de linha
  file.seek(0);
  int crlfCount = 0, lfCount = 0, crCount = 0;
  while (file.available()) {
    char c = file.read();
    if (c == '\r') {
      if (file.peek() == '\n') {
        crlfCount++;
        file.read(); // consome o \n
      } else {
        crCount++;
      }
    } else if (c == '\n') {
      lfCount++;
    }
  }

  Serial.println("   Terminadores de linha:");
  Serial.printf("   CRLF (\\r\\n): %d\n", crlfCount);
  Serial.printf("   LF (\\n): %d\n", lfCount);
  Serial.printf("   CR (\\r): %d\n", crCount);

  // Verifica BOM UTF-8
  file.seek(0);
  uint8_t bom[3];
  file.read(bom, 3);
  if (bom[0] == 0xEF && bom[1] == 0xBB && bom[2] == 0xBF) {
    Serial.println("   ⚠️  ALERTA: Arquivo contém BOM UTF-8 (pode causar problemas!)");
  } else {
    Serial.println("   ✅ Sem BOM UTF-8");
  }

  file.close();
}

// Exibe conteúdo completo de um arquivo
void catFile(const char* filename) {
  if (!LittleFS.exists(filename)) {
    Serial.printf("❌ Arquivo '%s' não existe!\n", filename);
    return;
  }

  File file = LittleFS.open(filename, "r");
  if (!file) {
    Serial.printf("❌ Erro ao abrir '%s'\n", filename);
    return;
  }

  Serial.printf("\n📄 Conteúdo de '%s':\n", filename);
  Serial.println("----------------------------------------");
  while (file.available()) {
    Serial.write(file.read());
  }
  Serial.println("\n----------------------------------------");
  file.close();
}

// Processa comandos via Serial
void processSerialCommand() {
  if (Serial.available() > 0) {
    String command = Serial.readStringUntil('\n');
    command.trim();
    
    if (command.equalsIgnoreCase("format")) {
      Serial.println("\n⚠️  CONFIRMAÇÃO NECESSÁRIA!");
      Serial.println("Digite 'SIM' para confirmar a formatação do LittleFS:");
      
      // Aguarda confirmação (timeout de 10 segundos)
      unsigned long startTime = millis();
      while (millis() - startTime < 10000) {
        if (Serial.available() > 0) {
          String confirmation = Serial.readStringUntil('\n');
          confirmation.trim();
          
          if (confirmation == "SIM") {
            formatLittleFS();
            Serial.println("🔄 Reiniciando ESP32 em 3 segundos...");
            delay(3000);
            ESP.restart();
          } else {
            Serial.println("❌ Formatação cancelada.");
          }
          return;
        }
        delay(100);
      }
      Serial.println("⏱️  Timeout - formatação cancelada.");
      
    } else if (command.equalsIgnoreCase("list") || command.equalsIgnoreCase("ls")) {
      listFiles();
      
    } else if (command.equalsIgnoreCase("check")) {
      Serial.println("\n🔍 Verificando todos os arquivos críticos:");
      checkFile("/server.crt");
      checkFile("/server.key");
      checkFile("/wifi_config.json");
      
    } else if (command.startsWith("check ")) {
      String filename = command.substring(6);
      filename.trim();
      if (!filename.startsWith("/")) filename = "/" + filename;
      checkFile(filename.c_str());
      
    } else if (command.startsWith("cat ")) {
      String filename = command.substring(4);
      filename.trim();
      if (!filename.startsWith("/")) filename = "/" + filename;
      catFile(filename.c_str());
      
    } else if (command.equalsIgnoreCase("help") || command == "?") {
      Serial.println("\n📋 Comandos disponíveis:");
      Serial.println("   format       - Formata o LittleFS (requer confirmação)");
      Serial.println("   list/ls      - Lista arquivos no LittleFS");
      Serial.println("   check        - Verifica todos os certificados");
      Serial.println("   check <file> - Verifica arquivo específico");
      Serial.println("   cat <file>   - Mostra conteúdo do arquivo");
      Serial.println("   help/?       - Mostra esta ajuda");
      Serial.println("\nExemplos:");
      Serial.println("   check server.crt");
      Serial.println("   cat wifi_config.json");
      Serial.println();
    } else if (command.length() > 0) {
      Serial.println("❓ Comando desconhecido. Digite 'help' para ver os comandos.");
    }
  }
}

// Lê credenciais WiFi do arquivo JSON no LittleFS
bool loadWiFiCredentials() {
  if (!LittleFS.exists("/wifi_config.json")) {
    Serial.println("❌ Arquivo wifi_config.json não encontrado");
    return false;
  }

  File file = LittleFS.open("/wifi_config.json", "r");
  if (!file || file.size() == 0) {
    Serial.println("❌ Erro ao abrir wifi_config.json (vazio?)");
    return false;
  }

  String content = file.readString();
  file.close();

  DynamicJsonDocument doc(1024);
  DeserializationError error = deserializeJson(doc, content);
  if (error) {
    Serial.println("❌ Erro ao parsear JSON: " + String(error.c_str()));
    return false;
  }

  wifi_ssid = doc["ssid"].as<String>();
  wifi_password = doc["password"].as<String>();

  Serial.println("✅ Credenciais WiFi carregadas:");
  Serial.println("   SSID: " + wifi_ssid);
  Serial.println("   Password: [OCULTO]");

  return true;
}

// Lê certificados e chave privada do LittleFS
bool loadCertificates() {
  if (!LittleFS.exists("/server.crt") || !LittleFS.exists("/server.key")) {
    Serial.println("❌ Certificados não encontrados no LittleFS!");
    return false;
  }

  File certFile = LittleFS.open("/server.crt", "r");
  File keyFile  = LittleFS.open("/server.key", "r");
  if (!certFile || !keyFile) {
    Serial.println("❌ Erro ao abrir server.crt ou server.key");
    return false;
  }

  size_t certLen = certFile.size();
  size_t keyLen  = keyFile.size();

  Serial.printf("📄 Tamanhos dos arquivos PEM: cert=%u bytes, key=%u bytes\n", certLen, keyLen);

  // Buffers temporários para ler PEM
  uint8_t* certPEM = new uint8_t[certLen + 1];
  uint8_t* keyPEM = new uint8_t[keyLen + 1];
  
  memset(certPEM, 0, certLen + 1);
  memset(keyPEM, 0, keyLen + 1);

  // Lê os arquivos PEM
  size_t certRead = certFile.read(certPEM, certLen);
  size_t keyRead = keyFile.read(keyPEM, keyLen);

  certFile.close();
  keyFile.close();

  // Adiciona null terminators explicitamente
  certPEM[certLen] = '\0';
  keyPEM[keyLen] = '\0';

  Serial.printf("📖 Bytes lidos PEM: cert=%u, key=%u\n", certRead, keyRead);
  
  // Verifica se são certificados PEM válidos
  if (certRead < 27 || strncmp((char*)certPEM, "-----BEGIN CERTIFICATE-----", 27) != 0) {
    Serial.println("❌ ERRO: server.crt não parece ser um certificado PEM válido!");
    delete[] certPEM;
    delete[] keyPEM;
    return false;
  }

  if (keyRead < 27 || (strncmp((char*)keyPEM, "-----BEGIN PRIVATE KEY-----", 27) != 0 && 
                        strncmp((char*)keyPEM, "-----BEGIN RSA PRIVATE KEY-----", 31) != 0)) {
    Serial.println("❌ ERRO: server.key não parece ser uma chave privada PEM válida!");
    delete[] certPEM;
    delete[] keyPEM;
    return false;
  }

  Serial.println("✅ Certificados PEM validados");

  // Libera buffers DER anteriores se existirem
  if (globalCertDER != nullptr) {
    delete[] globalCertDER;
    globalCertDER = nullptr;
  }
  if (globalKeyDER != nullptr) {
    delete[] globalKeyDER;
    globalKeyDER = nullptr;
  }

  // Converte PEM para DER (formato requerido pela biblioteca HTTPSServer)
  Serial.println("\n🔄 Convertendo certificados PEM → DER...");
  
  if (!convertPEMtoDER(certPEM, certLen, &globalCertDER, &globalCertDERLen, false)) {
    Serial.println("❌ Falha ao converter certificado para DER");
    delete[] certPEM;
    delete[] keyPEM;
    return false;
  }
  
  if (!convertPEMtoDER(keyPEM, keyLen, &globalKeyDER, &globalKeyDERLen, true)) {
    Serial.println("❌ Falha ao converter chave privada para DER");
    delete[] certPEM;
    delete[] keyPEM;
    delete[] globalCertDER;
    globalCertDER = nullptr;
    return false;
  }
  
  // Libera buffers PEM temporários
  delete[] certPEM;
  delete[] keyPEM;
  
  Serial.printf("\n📊 Tamanhos DER finais:\n");
  Serial.printf("   Certificado DER: %u bytes\n", globalCertDERLen);
  Serial.printf("   Chave DER: %u bytes\n\n", globalKeyDERLen);

  // Cria o servidor com os certificados em formato DER
  // IMPORTANTE: Os buffers GLOBAIS DER permanecerão em memória durante toda a execução!
  Serial.println("🔧 Criando SSLCert com formato DER...");
  SSLCert* newCert = new SSLCert(globalCertDER, globalCertDERLen, globalKeyDER, globalKeyDERLen);
  
  if (newCert == nullptr) {
    Serial.println("❌ ERRO: Falha ao criar SSLCert (retornou nullptr)!");
    return false;
  }
  
  Serial.println("🔧 Criando HTTPSServer...");
  secureServer = new HTTPSServer(newCert);

  Serial.println("✅ Certificados carregados do LittleFS");
  Serial.println("💾 Buffers mantidos em memória para uso contínuo");

  return true;
}

// ====================== ROTAS ======================
void handleRoot(HTTPRequest *req, HTTPResponse *res) {
  res->setStatusCode(200);
  res->setStatusText("OK");
  res->println("<h1>Servidor HTTPS ativo via LittleFS!</h1>");
}

void handleDados(HTTPRequest *req, HTTPResponse *res) {
  if (req->getMethod() == "POST") {
    std::string body;
    byte buffer[256];
    int read;

    while ((read = req->readBytes(buffer, sizeof(buffer))) > 0) {
      body.append((char*)buffer, read);
    }

    String bodyStr = String(body.c_str());

    Serial.println("=== 📩 DADOS RECEBIDOS DO APP ===");
    Serial.println(bodyStr);
    Serial.println("=================================");

    Serial1.println(bodyStr); // Envia para o Arduino Mega

    res->setStatusCode(200);
    res->setStatusText("OK");
    res->println("✅ JSON recebido com sucesso!");
  } else {
    res->setStatusCode(405);
    res->setStatusText("Method Not Allowed");
    res->println("Use POST para enviar dados.");
  }
}

// ====================== SETUP ======================
void setup() {
  Serial.begin(115200);
  Serial1.begin(9600);

  // Monta LittleFS
  if (!mountLittleFS()) {
    Serial.println("⚠️ Erro crítico: não foi possível montar LittleFS.");
    delay(10000);
    ESP.restart();
  }

  // Carrega credenciais Wi-Fi
  if (!loadWiFiCredentials()) {
    Serial.println("⚠️ ERRO: wifi_config.json ausente ou inválido!");
    delay(10000);
    ESP.restart();
  }

  // Conecta ao Wi-Fi
  Serial.println("Conectando ao Wi-Fi...");
  WiFi.begin(wifi_ssid.c_str(), wifi_password.c_str());
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println();
  Serial.println("✅ Wi-Fi conectado!");
  Serial.print("🌐 IP local: ");
  Serial.println(WiFi.localIP());

  // Carrega certificados
  if (!loadCertificates()) {
    Serial.println("⚠️ ERRO: Certificados ausentes ou inválidos!");
    delay(10000);
    ESP.restart();
  }

  // Registra rotas
  secureServer->registerNode(new ResourceNode("/", "GET", &handleRoot));
  secureServer->registerNode(new ResourceNode("/dados", "POST", &handleDados));

  // Inicia servidor HTTPS
  secureServer->start();
  Serial.println("🚀 Servidor HTTPS iniciado com sucesso!");
  Serial.println("\n💡 Digite 'help' no Monitor Serial para ver comandos disponíveis.");
}

// ====================== LOOP ======================
void loop() {
  // Processa comandos do Monitor Serial
  processSerialCommand();
  
  // Loop do servidor HTTPS
  if (secureServer != nullptr) {
    secureServer->loop();
  }
}