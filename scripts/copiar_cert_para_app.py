#!/usr/bin/env python3
"""
Script para copiar o certificado do ESP32 para o app Android
"""

import os
import sys

def main():
    print("=" * 70)
    print("📱 Copiar Certificado do ESP32 para o App Android")
    print("=" * 70)
    
    # Caminhos
    server_cert = "elevox-server/https_server/data/server.crt"
    app_cert = "elevox-app/app/src/main/res/raw/esp.crt"
    
    print("\n🔍 Verificando arquivos...")
    
    # Verifica se o certificado do servidor existe
    if not os.path.exists(server_cert):
        print(f"\n❌ Certificado do servidor não encontrado em: {server_cert}")
        print("\n📋 INSTRUÇÕES:")
        print("1. No Monitor Serial do ESP32, digite: cat server.crt")
        print("2. Copie TODA a saída (desde -----BEGIN até -----END)")
        print("3. Cole abaixo quando solicitado\n")
        
        print("=" * 70)
        print("Cole o certificado abaixo e pressione ENTER duas vezes:")
        print("=" * 70)
        
        lines = []
        print()
        while True:
            try:
                line = input()
                if line.strip() == "" and len(lines) > 0:
                    break
                if line.strip() != "":
                    lines.append(line)
            except EOFError:
                break
        
        if len(lines) == 0:
            print("\n❌ Nenhum certificado foi colado!")
            return 1
        
        cert_content = '\n'.join(lines) + '\n'
        
        # Valida que parece um certificado PEM
        if not cert_content.strip().startswith('-----BEGIN CERTIFICATE-----'):
            print("\n❌ Isso não parece ser um certificado PEM válido!")
            print("   Deve começar com: -----BEGIN CERTIFICATE-----")
            return 1
        
        if not '-----END CERTIFICATE-----' in cert_content:
            print("\n❌ Certificado incompleto!")
            print("   Deve terminar com: -----END CERTIFICATE-----")
            return 1
        
    else:
        # Lê do arquivo local
        print(f"✅ Certificado do servidor encontrado: {server_cert}")
        with open(server_cert, 'r', encoding='utf-8') as f:
            cert_content = f.read()
    
    # Cria diretórios se necessário
    app_cert_dir = os.path.dirname(app_cert)
    if not os.path.exists(app_cert_dir):
        os.makedirs(app_cert_dir)
        print(f"✅ Pasta criada: {app_cert_dir}")
    
    # Corrige formato
    print("\n🔧 Corrigindo formato...")
    
    # Remove BOM se existir
    if cert_content.startswith('\ufeff'):
        cert_content = cert_content[1:]
        print("   Removido BOM UTF-8")
    
    # Normaliza line endings para LF
    cert_content = cert_content.replace('\r\n', '\n').replace('\r', '\n')
    
    # Remove linhas vazias extras
    lines = [line.rstrip() for line in cert_content.split('\n')]
    while lines and lines[-1] == '':
        lines.pop()
    
    cert_content = '\n'.join(lines) + '\n'
    
    # Salva no app
    with open(app_cert, 'wb') as f:
        f.write(cert_content.encode('utf-8'))
    
    print(f"✅ Certificado salvo em: {app_cert}")
    print(f"   Tamanho: {len(cert_content)} bytes")
    print(f"   Linhas: {len(lines)}")
    
    # Verifica formato
    print("\n📊 Validação:")
    if cert_content.startswith('-----BEGIN CERTIFICATE-----'):
        print("   ✅ Início correto: -----BEGIN CERTIFICATE-----")
    else:
        print("   ❌ ERRO: Não começa com -----BEGIN CERTIFICATE-----")
        return 1
    
    if '-----END CERTIFICATE-----' in cert_content:
        print("   ✅ Fim correto: -----END CERTIFICATE-----")
    else:
        print("   ❌ ERRO: Não termina com -----END CERTIFICATE-----")
        return 1
    
    # Conta terminadores de linha
    crlf = cert_content.count('\r\n')
    lf = cert_content.count('\n') - crlf
    cr = cert_content.count('\r') - crlf
    
    print(f"   Terminadores: CRLF={crlf}, LF={lf}, CR={cr}")
    if crlf == 0 and cr == 0:
        print("   ✅ Apenas LF (Unix) - correto!")
    else:
        print("   ⚠️  Aviso: Contém CRLF ou CR")
    
    print("\n" + "=" * 70)
    print("✅ SUCESSO! Certificado copiado para o app")
    print("=" * 70)
    
    print("\n📋 Próximos passos:")
    print("1. Compile o app Android novamente")
    print("2. Instale no dispositivo")
    print("3. Teste a conexão com o ESP32")
    
    print("\n💡 Dica: O arquivo esp.crt NÃO deve ser commitado no Git")
    print("   (já está no .gitignore)")
    
    return 0

if __name__ == '__main__':
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        print("\n\n⚠️  Interrompido pelo usuário")
        sys.exit(1)
    except Exception as e:
        print(f"\n❌ Erro: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)

