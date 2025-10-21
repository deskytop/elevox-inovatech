# 🏢 Elevox - Sistema de Controle de Elevador via HTTPS

Sistema IoT para controle de elevadores usando ESP32 e aplicativo Android com comunicação segura HTTPS.

[![ESP32](https://img.shields.io/badge/ESP32-Arduino-red)](https://www.espressif.com/)
[![Android](https://img.shields.io/badge/Android-Kotlin%20%2B%20Compose-green)](https://developer.android.com/)

---

## 📋 Índice

- [Visão Geral](#-visão-geral)
- [Arquitetura](#-arquitetura)
- [Segurança](#-segurança)
- [Pré-requisitos](#-pré-requisitos)
- [Instalação](#-instalação)
- [Uso](#-uso)
- [Scripts](#-scripts)
- [Estrutura do Projeto](#-estrutura-do-projeto)

---

## 🎯 Visão Geral

O **Elevox** é um sistema de controle de elevadores que permite:

- 📱 **Controle via App Android** - Interface moderna em Jetpack Compose
- 🔐 **Comunicação Segura** - HTTPS com certificate pinning
- 🌐 **Rede Local** - Comunicação WiFi entre app e ESP32
- ⚡ **Tempo Real** - Comandos instantâneos via HTTPS
- 🛠️ **IoT Moderno** - ESP32 como servidor HTTPS

---

## 🏗️ Arquitetura

```
┌─────────────────┐                    ┌─────────────────┐
│                 │                    │                 │
│  App Android    │  HTTPS (TLS 1.2+) │   ESP32 Server  │
│  (Kotlin)       │◄──────────────────►│   (Arduino)     │
│                 │  Certificate Pin   │                 │
│  - Jetpack      │                    │  - WiFi AP      │
│    Compose      │                    │  - LittleFS     │
│  - Retrofit     │                    │  - HTTPSServer  │
│  - OkHttp       │                    │  - mbedtls      │
│                 │                    │                 │
└─────────────────┘                    └────────┬────────┘
                                                │
                                                │ Serial
                                                ▼
                                       ┌─────────────────┐
                                       │                 │
                                       │  Arduino Mega   │
                                       │  (Controle)     │
                                       │                 │
                                       └─────────────────┘
```

---

## 🔒 Segurança

O projeto implementa múltiplas camadas de segurança:

### ✅ Implementado

- **HTTPS (TLS 1.2+)** - Toda comunicação criptografada
- **Certificate Pinning** - SPKI SHA-256 no app
- **Auto-signed Certificates** - Certificados gerados localmente
- **Hostname Verification** - Validação do hostname do servidor
- **Timeouts Agressivos** - 5s connect, 10s read
- **Sensitive Data Protection** - `.gitignore` robusto

### 📖 Documentação

**⚠️ IMPORTANTE:** Este projeto usa certificados auto-assinados adequados para **desenvolvimento**. Para produção, use certificados válidos.

---

## 📦 Pré-requisitos

### Para o App Android

- **Android Studio** Arctic Fox ou superior
- **JDK** 11 ou superior
- **Android SDK** 24+ (Android 7.0+)
- **Gradle** 8.0+

### Para o ESP32

- **Arduino IDE** 2.0+ ou **PlatformIO**
- **ESP32 Board** (testado com ESP32 Bluetooth 38 Pinos CP2102)
- **Biblioteca HTTPSServer** ([esp32_https_server](https://github.com/fhessel/esp32_https_server))
- **Arduino Mega** (opcional, para controle do elevador)

### Ferramentas Auxiliares

- **Python 3.7+** (para scripts utilitários)
- **OpenSSL** (para geração de certificados)

---

## 🚀 Instalação

### 1. Clone o Repositório

```bash
git clone https://github.com/seu-usuario/elevox.git
cd elevox
```

### 2. Configure o ESP32

```bash
# 1. Gere certificados SSL
cd scripts
python gerar_cert_esp32.py

# 2. Configure WiFi
cd ../elevox-server/https_server/data
cp wifi_config.json.example wifi_config.json
# Edite wifi_config.json com suas credenciais

# 3. Faça upload do código
# No Arduino IDE: Sketch → Upload
# E depois: Tools → ESP32 Sketch Data Upload
```

ou siga instruções: https://randomnerdtutorials.com/arduino-ide-2-install-esp32-littlefs/

### 3. Configure o App Android

```bash
cd elevox-app

# 1. Configure local.properties
cp local.properties.example local.properties
# Edite com o IP do ESP32

# 2. Copie o certificado
cd ../scripts
python copiar_cert_para_app.py

# 3. Compile o app
cd ../elevox-app
./gradlew :app:assembleDebug

# 4. Instale no dispositivo
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 💡 Uso

### Testando a Conexão

1. **Certifique-se** de que o ESP32 está conectado ao WiFi
2. **Abra o app** no dispositivo Android
3. **Conecte** o dispositivo à mesma rede WiFi do ESP32
4. **Toque** em "Testar Conexão"
5. **Aguarde** a confirmação de sucesso

### Monitor Serial (ESP32)

O ESP32 oferece comandos via Monitor Serial (115200 baud):

```
help         - Mostra comandos disponíveis
list         - Lista arquivos no LittleFS
check        - Verifica certificados
cat <file>   - Mostra conteúdo de arquivo
format       - Formata o LittleFS (requer confirmação)
```

### Logs do App

Para ver logs detalhados no Android:

```bash
adb logcat -s HTTPS:* ApiClient:*
```

---

## 🛠️ Scripts

Os scripts na pasta `scripts/` auxiliam na configuração:

| Script | Descrição |
|--------|-----------|
| `gerar_cert_esp32.py` | Gera certificados SSL para ESP32 |
| `copiar_cert_para_app.py` | Copia certificado para o app |
| `fix_certificates.py` | Corrige problemas de formato |

Ver [scripts/README.md](scripts/README.md) para documentação completa.

---

## 📁 Estrutura do Projeto

```
elevox/
├── elevox-app/                 # Aplicativo Android
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── java/com/elevox/app/
│   │   │   │   ├── net/        # Camada de rede (HTTPS)
│   │   │   │   ├── data/       # Repositórios
│   │   │   │   ├── home/       # UI Principal
│   │   │   │   └── ...
│   │   │   └── res/
│   │   │       ├── raw/        # Certificados (esp.crt)
│   │   │       └── ...
│   │   └── build.gradle.kts
│   ├── local.properties        # Config local (gitignored)
│   └── AGENTS.md               # Guia de desenvolvimento
│
├── elevox-server/              # Servidor ESP32
│   └── https_server/
│       ├── https_server.ino    # Código principal
│       ├── data/               # Dados do LittleFS (gitignored)
│       │   ├── server.crt      # Certificado SSL
│       │   ├── server.key      # Chave privada
│       │   └── wifi_config.json
│       └── data-example/       # Templates (commitados)
│
├── scripts/                    # Scripts utilitários
│   ├── README.md
│   ├── gerar_cert_esp32.py
│   ├── copiar_cert_para_app.py
│   └── fix_certificates.py
│
├── .gitignore                  # Proteção de arquivos sensíveis
└── README.md                   # Este arquivo
```

---

## 🐛 Troubleshooting

### Erro: `mbedtls_x509_crt_parse returned -0x2180`

**Causa:** Formato incorreto de certificado (PEM vs DER)

**Solução:**
```bash
cd scripts
python fix_certificates.py
# Escolha opção 2 para corrigir
```

### App não conecta ao ESP32

**Checklist:**
- [ ] ESP32 e smartphone na mesma rede WiFi
- [ ] IP correto no `local.properties`
- [ ] Certificado `esp.crt` copiado para o app
- [ ] ESP32 mostra "Servidor HTTPS iniciado"

### Monitor Serial mostra erro ao carregar certificados

```bash
# No Monitor Serial (115200 baud):
check

# Deve mostrar:
# ✅ Certificados validados como PEM
# ✅ Conversão PEM→DER bem-sucedida
```

---
