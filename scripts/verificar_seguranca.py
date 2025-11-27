#!/usr/bin/env python3
"""
Script de verificação de segurança antes de commit
Verifica se há arquivos sensíveis sendo commitados
"""

import os
import sys
import subprocess

# Arquivos/padrões sensíveis que NÃO devem ser commitados
PADROES_SENSIVEIS = [
    # Certificados e chaves
    ('*.key', 'Chave privada'),
    ('*.crt', 'Certificado SSL (exceto examples)'),
    ('*.pem', 'Certificado PEM'),
    ('*.p12', 'PKCS#12'),
    ('*.pk8', 'PKCS#8'),
    ('*.der', 'Formato DER'),
    ('*.jks', 'Java Keystore'),
    ('*.keystore', 'Android Keystore'),
    
    # Configurações
    ('local.properties', 'Configurações locais'),
    ('wifi_config.json', 'Credenciais WiFi'),
    
    # Arquivos específicos
    ('elevox-app/app/src/main/res/raw/esp.crt', 'Certificado real do dispositivo'),
    ('elevox-server/https_server/data/', 'Dados sensíveis do ESP32'),
]

# Arquivos permitidos (examples)
PADROES_PERMITIDOS = [
    '.example',
    'example.',
    '_example',
]

# Cores para output
class Cores:
    VERMELHO = '\033[91m'
    VERDE = '\033[92m'
    AMARELO = '\033[93m'
    AZUL = '\033[94m'
    RESET = '\033[0m'
    BOLD = '\033[1m'

def executar_comando(cmd):
    """Executa comando e retorna output"""
    try:
        result = subprocess.run(
            cmd,
            shell=True,
            capture_output=True,
            text=True
        )
        return result.returncode == 0, result.stdout, result.stderr
    except Exception as e:
        return False, "", str(e)

def eh_arquivo_exemplo(arquivo):
    """Verifica se é um arquivo de exemplo"""
    return any(padrao in arquivo for padrao in PADROES_PERMITIDOS)

def verificar_arquivos_staged():
    """Verifica arquivos na staged area do git"""
    print(f"\n{Cores.AZUL}{Cores.BOLD}🔍 Verificando arquivos staged...{Cores.RESET}\n")
    
    # Verifica se é um repositório git
    sucesso, _, _ = executar_comando("git rev-parse --git-dir")
    if not sucesso:
        print(f"{Cores.AMARELO}⚠️  Não é um repositório Git{Cores.RESET}")
        return True
    
    # Obtém lista de arquivos staged
    sucesso, output, _ = executar_comando("git diff --cached --name-only")
    if not sucesso:
        print(f"{Cores.VERMELHO}❌ Erro ao obter arquivos staged{Cores.RESET}")
        return False
    
    arquivos_staged = [f.strip() for f in output.split('\n') if f.strip()]
    
    if not arquivos_staged:
        print(f"{Cores.AMARELO}📭 Nenhum arquivo staged{Cores.RESET}")
        return True
    
    print(f"📋 {len(arquivos_staged)} arquivo(s) staged:")
    for arquivo in arquivos_staged:
        print(f"   - {arquivo}")
    
    # Verifica cada arquivo
    problemas = []
    
    for arquivo in arquivos_staged:
        # Pula se for arquivo de exemplo
        if eh_arquivo_exemplo(arquivo):
            continue
        
        # Verifica contra padrões sensíveis
        for padrao, descricao in PADROES_SENSIVEIS:
            if padrao.startswith('*'):
                # Padrão de extensão
                if arquivo.endswith(padrao[1:]):
                    problemas.append((arquivo, descricao))
                    break
            else:
                # Padrão de caminho
                if padrao in arquivo:
                    problemas.append((arquivo, descricao))
                    break
    
    return problemas

def verificar_conteudo_sensivel(arquivos_staged):
    """Verifica conteúdo dos arquivos por padrões sensíveis"""
    print(f"\n{Cores.AZUL}{Cores.BOLD}🔎 Verificando conteúdo dos arquivos...{Cores.RESET}\n")
    
    padroes_conteudo = [
        'password=',
        'api_key=',
        'secret=',
        'token=',
        'private_key=',
        '-----BEGIN PRIVATE KEY-----',
        '-----BEGIN RSA PRIVATE KEY-----',
    ]
    
    problemas = []
    
    for arquivo in arquivos_staged:
        # Pula arquivos binários e de exemplo
        if eh_arquivo_exemplo(arquivo):
            continue
        
        if arquivo.endswith(('.png', '.jpg', '.jpeg', '.gif', '.apk', '.jar', '.so', '.bin')):
            continue
        
        try:
            sucesso, output, _ = executar_comando(f'git diff --cached {arquivo}')
            if sucesso:
                content_lower = output.lower()
                for padrao in padroes_conteudo:
                    if padrao.lower() in content_lower:
                        problemas.append((arquivo, f"Contém '{padrao}'"))
                        break
        except:
            pass
    
    return problemas

def main():
    """Função principal"""
    print(f"\n{Cores.BOLD}{'='*60}{Cores.RESET}")
    print(f"{Cores.BOLD}🔒 Verificação de Segurança - Elevox{Cores.RESET}")
    print(f"{Cores.BOLD}{'='*60}{Cores.RESET}")
    
    # Verifica arquivos staged
    problemas_arquivos = verificar_arquivos_staged()
    
    if problemas_arquivos is True:
        # Sem problemas, mas também sem arquivos staged
        return 0
    
    # Verifica conteúdo
    arquivos_staged = []
    sucesso, output, _ = executar_comando("git diff --cached --name-only")
    if sucesso:
        arquivos_staged = [f.strip() for f in output.split('\n') if f.strip()]
    
    problemas_conteudo = verificar_conteudo_sensivel(arquivos_staged)
    
    # Resultados
    print(f"\n{Cores.BOLD}{'='*60}{Cores.RESET}")
    print(f"{Cores.BOLD}📊 Resultados{Cores.RESET}")
    print(f"{Cores.BOLD}{'='*60}{Cores.RESET}\n")
    
    total_problemas = len(problemas_arquivos) + len(problemas_conteudo)
    
    if total_problemas == 0:
        print(f"{Cores.VERDE}{Cores.BOLD}✅ Nenhum problema de segurança detectado!{Cores.RESET}")
        print(f"{Cores.VERDE}   Seguro para commit.{Cores.RESET}\n")
        return 0
    
    # Mostra problemas de arquivos
    if problemas_arquivos:
        print(f"{Cores.VERMELHO}{Cores.BOLD}❌ Arquivos sensíveis detectados:{Cores.RESET}\n")
        for arquivo, descricao in problemas_arquivos:
            print(f"{Cores.VERMELHO}   ⚠️  {arquivo}{Cores.RESET}")
            print(f"      Motivo: {descricao}\n")
    
    # Mostra problemas de conteúdo
    if problemas_conteudo:
        print(f"{Cores.AMARELO}{Cores.BOLD}⚠️  Conteúdo sensível detectado:{Cores.RESET}\n")
        for arquivo, descricao in problemas_conteudo:
            print(f"{Cores.AMARELO}   ⚠️  {arquivo}{Cores.RESET}")
            print(f"      Motivo: {descricao}\n")
    
    # Instruções de correção
    print(f"{Cores.BOLD}{'='*60}{Cores.RESET}")
    print(f"{Cores.BOLD}🔧 Como corrigir:{Cores.RESET}")
    print(f"{Cores.BOLD}{'='*60}{Cores.RESET}\n")
    
    print("1. Remova os arquivos sensíveis da staged area:")
    print(f"{Cores.AZUL}   git reset HEAD arquivo-sensivel{Cores.RESET}\n")
    
    print("2. Adicione os arquivos ao .gitignore (se ainda não estiverem):")
    print(f"{Cores.AZUL}   echo 'arquivo-sensivel' >> .gitignore{Cores.RESET}\n")
    
    print("3. Para conteúdo sensível, remova do arquivo ou use variáveis de ambiente\n")
    
    print(f"{Cores.VERMELHO}{Cores.BOLD}❌ NÃO FAÇA COMMIT COM ESSES PROBLEMAS!{Cores.RESET}\n")
    
    return 1

if __name__ == '__main__':
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        print(f"\n\n{Cores.AMARELO}⚠️  Interrompido pelo usuário{Cores.RESET}")
        sys.exit(1)
    except Exception as e:
        print(f"\n{Cores.VERMELHO}❌ Erro: {e}{Cores.RESET}")
        sys.exit(1)








