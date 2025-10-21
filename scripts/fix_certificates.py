#!/usr/bin/env python3
"""
Script para corrigir e validar certificados SSL para ESP32
Remove BOM, normaliza terminadores de linha e valida formato PEM
"""

import os
import sys

def fix_certificate_file(input_path, output_path=None):
    """
    Corrige um arquivo de certificado/chave:
    - Remove BOM UTF-8
    - Converte CRLF para LF
    - Remove espaços em branco extras
    - Garante quebra de linha no final
    """
    
    if not os.path.exists(input_path):
        print(f"❌ Erro: Arquivo '{input_path}' não encontrado!")
        return False
    
    print(f"\n🔧 Processando: {input_path}")
    
    # Lê o arquivo em modo binário
    with open(input_path, 'rb') as f:
        content = f.read()
    
    # Informações originais
    original_size = len(content)
    print(f"   Tamanho original: {original_size} bytes")
    
    # Verifica e remove BOM UTF-8
    if content.startswith(b'\xef\xbb\xbf'):
        print("   ⚠️  Removendo BOM UTF-8...")
        content = content[3:]
    
    # Converte para string
    try:
        text = content.decode('utf-8')
    except UnicodeDecodeError:
        print("   ❌ Erro: Arquivo não está em UTF-8!")
        return False
    
    # Normaliza terminadores de linha (CRLF -> LF)
    original_lines = text.count('\r\n') + text.count('\r') + text.count('\n')
    text = text.replace('\r\n', '\n').replace('\r', '\n')
    
    # Remove espaços no final de cada linha
    lines = text.split('\n')
    lines = [line.rstrip() for line in lines]
    
    # Remove linhas vazias extras no final
    while lines and lines[-1] == '':
        lines.pop()
    
    # Garante quebra de linha no final
    text = '\n'.join(lines) + '\n'
    
    print(f"   Linhas processadas: {len(lines)}")
    
    # Valida formato PEM
    is_cert = text.startswith('-----BEGIN CERTIFICATE-----')
    is_key = text.startswith('-----BEGIN PRIVATE KEY-----') or \
             text.startswith('-----BEGIN RSA PRIVATE KEY-----') or \
             text.startswith('-----BEGIN EC PRIVATE KEY-----')
    
    if not is_cert and not is_key:
        print("   ⚠️  AVISO: Arquivo não parece ser um certificado ou chave PEM válida!")
        print(f"   Primeiros caracteres: {text[:50]}")
    else:
        if is_cert:
            print("   ✅ Certificado PEM válido detectado")
        if is_key:
            print("   ✅ Chave privada PEM válida detectada")
    
    # Verifica término correto
    if is_cert and not '-----END CERTIFICATE-----' in text:
        print("   ❌ ERRO: Certificado não tem -----END CERTIFICATE-----")
        return False
    
    if is_key:
        has_end = ('-----END PRIVATE KEY-----' in text or 
                   '-----END RSA PRIVATE KEY-----' in text or
                   '-----END EC PRIVATE KEY-----' in text)
        if not has_end:
            print("   ❌ ERRO: Chave privada não tem marcador END correto")
            return False
    
    # Converte de volta para bytes (UTF-8 sem BOM, LF apenas)
    content_fixed = text.encode('utf-8')
    
    # Define caminho de saída
    if output_path is None:
        # Sobrescreve o arquivo original
        output_path = input_path
    
    # Salva arquivo corrigido
    with open(output_path, 'wb') as f:
        f.write(content_fixed)
    
    new_size = len(content_fixed)
    print(f"   Tamanho final: {new_size} bytes")
    
    if original_size != new_size:
        print(f"   📊 Diferença: {new_size - original_size:+d} bytes")
    
    print(f"   ✅ Arquivo salvo: {output_path}")
    
    return True

def check_file_details(file_path):
    """
    Mostra informações detalhadas sobre um arquivo
    """
    if not os.path.exists(file_path):
        print(f"❌ Erro: Arquivo '{file_path}' não encontrado!")
        return
    
    print(f"\n🔍 Análise detalhada: {file_path}")
    print("=" * 60)
    
    with open(file_path, 'rb') as f:
        content = f.read()
    
    print(f"Tamanho: {len(content)} bytes")
    
    # Verifica BOM
    if content.startswith(b'\xef\xbb\xbf'):
        print("❌ BOM UTF-8: PRESENTE (isso vai causar problemas!)")
    else:
        print("✅ BOM UTF-8: Ausente")
    
    # Verifica codificação
    try:
        text = content.decode('utf-8')
        print("✅ Codificação: UTF-8 válido")
    except UnicodeDecodeError:
        print("❌ Codificação: Não é UTF-8 válido!")
        return
    
    # Terminadores de linha
    crlf_count = text.count('\r\n')
    lf_count = text.count('\n') - crlf_count
    cr_count = text.count('\r') - crlf_count
    
    print(f"\nTerminadores de linha:")
    print(f"  CRLF (\\r\\n): {crlf_count}")
    print(f"  LF (\\n): {lf_count}")
    print(f"  CR (\\r): {cr_count}")
    
    if crlf_count > 0:
        print("  ⚠️  Windows line endings detectados (CRLF) - será convertido para LF")
    elif lf_count > 0:
        print("  ✅ Unix line endings (LF)")
    
    # Verifica formato PEM
    print("\nValidação PEM:")
    if text.startswith('-----BEGIN CERTIFICATE-----'):
        print("  ✅ Início de certificado válido")
        if '-----END CERTIFICATE-----' in text:
            print("  ✅ Fim de certificado válido")
        else:
            print("  ❌ Fim de certificado NÃO encontrado!")
    elif text.startswith('-----BEGIN PRIVATE KEY-----'):
        print("  ✅ Início de chave privada válido (PKCS#8)")
        if '-----END PRIVATE KEY-----' in text:
            print("  ✅ Fim de chave privada válido")
        else:
            print("  ❌ Fim de chave privada NÃO encontrado!")
    elif text.startswith('-----BEGIN RSA PRIVATE KEY-----'):
        print("  ✅ Início de chave RSA válido (PKCS#1)")
        if '-----END RSA PRIVATE KEY-----' in text:
            print("  ✅ Fim de chave RSA válido")
        else:
            print("  ❌ Fim de chave RSA NÃO encontrado!")
    else:
        print(f"  ❌ Formato PEM não reconhecido!")
        print(f"  Primeiros 50 caracteres: {text[:50]}")
    
    # Primeiros bytes em hex
    print(f"\nPrimeiros 32 bytes (HEX):")
    hex_str = ' '.join(f'{b:02X}' for b in content[:32])
    print(f"  {hex_str}")
    
    print("\nPrimeiros 100 caracteres:")
    print(f"  {text[:100]}")
    print("=" * 60)

def main():
    """
    Função principal
    """
    print("=" * 60)
    print("🔧 Corretor de Certificados SSL para ESP32")
    print("=" * 60)
    
    # Encontra arquivos de certificado na pasta data
    data_dir = os.path.join(os.path.dirname(__file__), 'data')
    
    if not os.path.exists(data_dir):
        print(f"\n⚠️  Pasta 'data' não encontrada em: {data_dir}")
        print("Criando pasta 'data'...")
        os.makedirs(data_dir)
    
    cert_file = os.path.join(data_dir, 'server.crt')
    key_file = os.path.join(data_dir, 'server.key')
    
    files_to_fix = []
    
    if os.path.exists(cert_file):
        files_to_fix.append(cert_file)
    else:
        print(f"\n⚠️  Arquivo não encontrado: {cert_file}")
    
    if os.path.exists(key_file):
        files_to_fix.append(key_file)
    else:
        print(f"\n⚠️  Arquivo não encontrado: {key_file}")
    
    if not files_to_fix:
        print("\n❌ Nenhum arquivo para processar!")
        print("\nColoque os arquivos server.crt e server.key na pasta 'data/'")
        return 1
    
    # Opções
    print("\nOpções:")
    print("1 - Analisar arquivos (sem modificar)")
    print("2 - Corrigir e sobrescrever arquivos")
    print("3 - Sair")
    
    choice = input("\nEscolha uma opção (1-3): ").strip()
    
    if choice == '1':
        # Apenas análise
        for file_path in files_to_fix:
            check_file_details(file_path)
    
    elif choice == '2':
        # Corrigir arquivos
        print("\n⚠️  ATENÇÃO: Isso vai sobrescrever os arquivos originais!")
        confirm = input("Deseja continuar? (S/N): ").strip().upper()
        
        if confirm == 'S' or confirm == 'SIM':
            success_count = 0
            for file_path in files_to_fix:
                if fix_certificate_file(file_path):
                    success_count += 1
            
            print(f"\n✅ {success_count}/{len(files_to_fix)} arquivos processados com sucesso!")
            print("\n📤 Próximos passos:")
            print("1. Use o Arduino IDE: Tools > ESP32 Sketch Data Upload")
            print("2. Ou use esptool.py para fazer upload manual")
            print("3. Compile e envie o código para o ESP32")
        else:
            print("❌ Operação cancelada.")
    
    elif choice == '3':
        print("👋 Até logo!")
        return 0
    
    else:
        print("❌ Opção inválida!")
        return 1
    
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

