#!/usr/bin/env python3
"""
Gera certificados SSL otimizados para ESP32 com formato garantido
"""

import subprocess
import os
import sys

def run_command(cmd, shell=True):
    """Executa comando e retorna resultado"""
    try:
        result = subprocess.run(cmd, shell=shell, capture_output=True, text=True)
        return result.returncode == 0, result.stdout, result.stderr
    except Exception as e:
        return False, "", str(e)

def generate_certificates():
    """Gera certificados no formato correto para ESP32"""
    
    print("=" * 60)
    print("🔐 Gerador de Certificados para ESP32")
    print("=" * 60)
    
    # Verifica se OpenSSL está disponível
    success, stdout, stderr = run_command("openssl version")
    if not success:
        print("\n❌ OpenSSL não encontrado!")
        print("Instale o OpenSSL:")
        print("  Windows: https://slproweb.com/products/Win32OpenSSL.html")
        print("  Linux: sudo apt-get install openssl")
        print("  Mac: brew install openssl")
        return False
    
    print(f"\n✅ OpenSSL encontrado: {stdout.strip()}")
    
    # Cria pasta data se não existir
    data_dir = os.path.join(os.path.dirname(__file__), 'data')
    if not os.path.exists(data_dir):
        os.makedirs(data_dir)
        print(f"✅ Pasta 'data' criada")
    
    os.chdir(data_dir)
    print(f"📁 Diretório: {data_dir}")
    
    # Backup de arquivos existentes
    for filename in ['server.crt', 'server.key']:
        if os.path.exists(filename):
            backup = f"{filename}.backup"
            os.rename(filename, backup)
            print(f"💾 Backup: {filename} -> {backup}")
    
    print("\n🔧 Gerando chave privada RSA 2048 bits...")
    
    # Gera chave privada
    cmd = "openssl genrsa -out server.key 2048"
    success, stdout, stderr = run_command(cmd)
    if not success:
        print(f"❌ Erro ao gerar chave: {stderr}")
        return False
    
    print("✅ Chave privada gerada")
    
    print("\n🔧 Gerando certificado auto-assinado...")
    
    # Gera certificado
    cmd = (
        'openssl req -new -x509 '
        '-key server.key '
        '-out server.crt '
        '-days 3650 '
        '-subj "/C=BR/ST=State/L=City/O=ESP32/OU=IoT/CN=esp32.local"'
    )
    success, stdout, stderr = run_command(cmd)
    if not success:
        print(f"❌ Erro ao gerar certificado: {stderr}")
        return False
    
    print("✅ Certificado gerado (válido por 10 anos)")
    
    print("\n🔧 Corrigindo formato dos arquivos...")
    
    # Corrige formato de ambos os arquivos
    for filename in ['server.crt', 'server.key']:
        with open(filename, 'rb') as f:
            content = f.read()
        
        # Remove BOM se existir
        if content.startswith(b'\xef\xbb\xbf'):
            content = content[3:]
            print(f"   Removido BOM de {filename}")
        
        # Converte para texto
        text = content.decode('utf-8')
        
        # Normaliza line endings para LF
        text = text.replace('\r\n', '\n').replace('\r', '\n')
        
        # Remove espaços no final das linhas
        lines = [line.rstrip() for line in text.split('\n')]
        
        # Remove linhas vazias extras no final
        while lines and lines[-1] == '':
            lines.pop()
        
        # Reconstrói com LF e garante newline no final
        text = '\n'.join(lines) + '\n'
        
        # Salva em UTF-8 sem BOM, apenas LF
        with open(filename, 'wb') as f:
            f.write(text.encode('utf-8'))
        
        print(f"   ✅ {filename} corrigido ({len(text)} bytes)")
    
    print("\n📊 Validando certificados...")
    
    # Valida certificado
    success, stdout, stderr = run_command("openssl x509 -in server.crt -text -noout")
    if success:
        print("   ✅ Certificado válido")
        # Extrai informações relevantes
        for line in stdout.split('\n'):
            if 'Subject:' in line or 'Not Before' in line or 'Not After' in line:
                print(f"      {line.strip()}")
    else:
        print(f"   ⚠️  Aviso: {stderr}")
    
    # Valida chave
    success, stdout, stderr = run_command("openssl rsa -in server.key -check -noout")
    if success:
        print("   ✅ Chave privada válida")
    else:
        print(f"   ⚠️  Aviso: {stderr}")
    
    print("\n" + "=" * 60)
    print("✅ Certificados gerados com sucesso!")
    print("=" * 60)
    
    print("\n📋 Próximos passos:")
    print("1. No Arduino IDE: Tools → ESP32 Sketch Data Upload")
    print("2. Ou formate o LittleFS no ESP32:")
    print("   - Monitor Serial → digite 'format' → 'SIM'")
    print("3. Faça upload dos arquivos da pasta 'data'")
    print("4. Reinicie o ESP32")
    
    print("\n📄 Arquivos gerados:")
    for filename in ['server.crt', 'server.key']:
        size = os.path.getsize(filename)
        print(f"   {filename}: {size} bytes")
    
    return True

if __name__ == '__main__':
    try:
        success = generate_certificates()
        sys.exit(0 if success else 1)
    except KeyboardInterrupt:
        print("\n\n⚠️  Interrompido pelo usuário")
        sys.exit(1)
    except Exception as e:
        print(f"\n❌ Erro: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)

