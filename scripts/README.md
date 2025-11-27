# 🛠️ Scripts Utilitários - Elevox

Scripts Python para auxiliar na configuração e manutenção do projeto Elevox.

---

## 📋 Lista de Scripts

### 1. `copiar_cert_para_app.py`

**Propósito:** Copia certificados SSL do servidor ESP32 para o app Android.

**Uso:**
```bash
cd scripts
python copiar_cert_para_app.py
```

**O que faz:**
- Lê o certificado `server.crt` do ESP32
- Converte formato (remove BOM, normaliza line endings)
- Copia para `elevox-app/app/src/main/res/raw/esp.crt`
- Valida formato PEM

**Quando usar:**
- Após gerar novos certificados no ESP32
- Ao configurar o app pela primeira vez
- Se o app reportar erro de certificado

---

### 2. `fix_certificates.py`

**Propósito:** Corrige problemas de formato em certificados SSL.

**Uso:**
```bash
cd scripts
python fix_certificates.py
```

**Opções:**
1. **Analisar arquivos** - Mostra informações sobre os certificados
2. **Corrigir e sobrescrever** - Corrige problemas automaticamente

**Problemas que corrige:**
- ✅ Remove BOM UTF-8
- ✅ Converte CRLF → LF (Unix line endings)
- ✅ Remove espaços extras
- ✅ Garante quebra de linha no final

**Quando usar:**
- Erro `-0x2180` (MBEDTLS_ERR_X509_INVALID_FORMAT)
- Certificados gerados no Windows
- Após editar certificados manualmente

---

### 3. `gerar_cert_esp32.py`

**Propósito:** Gera certificados SSL auto-assinados otimizados para ESP32.

**Uso:**
```bash
cd scripts
python gerar_cert_esp32.py
```

**Requisitos:**
- OpenSSL instalado no sistema

**O que gera:**
- `elevox-server/https_server/data/server.crt` (certificado público)
- `elevox-server/https_server/data/server.key` (chave privada)
- Válido por 10 anos
- Formato garantido compatível com ESP32

**Quando usar:**
- Primeira configuração do projeto
- Renovação de certificados
- Após formatar LittleFS do ESP32

---

## 🔐 Considerações de Segurança

⚠️ **IMPORTANTE:**

1. **Certificados gerados são auto-assinados:**
   - Adequados para desenvolvimento
   - **NÃO use em produção** sem certificados válidos

2. **Chaves privadas:**
   - Mantidas localmente
   - Não compartilhar por canais inseguros
   - Regenerar se comprometidas

---

## 📝 Workflow Típico

### Setup Inicial

```bash
# 1. Gera certificados para ESP32
python gerar_cert_esp32.py

# 2. Faz upload para ESP32 (Arduino IDE: Tools → ESP32 Sketch Data Upload)

# 3. Copia certificado para o app
python copiar_cert_para_app.py

# 4. Compila e instala o app
cd ../elevox-app
./gradlew :app:assembleDebug
```

### Se encontrar erros

```bash
# 1. Analisa certificados
python fix_certificates.py
# Escolhe opção 1

# 2. Se houver problemas, corrige
python fix_certificates.py
# Escolhe opção 2

# 3. Copia para o app novamente
python copiar_cert_para_app.py
```

---

## 🆘 Troubleshooting

### OpenSSL não encontrado

**Windows:**
```bash
# Baixe de: https://slproweb.com/products/Win32OpenSSL.html
# Ou via Chocolatey:
choco install openssl
```

**Linux:**
```bash
sudo apt-get install openssl
```

**macOS:**
```bash
brew install openssl
```

### Erro ao copiar certificado

Certifique-se de que:
- O ESP32 já tem certificados gerados
- A pasta `elevox-app/app/src/main/res/raw/` existe
- Você tem permissões de escrita

### Scripts não executam

```bash
# Certifique-se de que Python 3 está instalado
python --version

# Se necessário, use python3
python3 script.py
```

---





