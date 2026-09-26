import socket
import ssl
import re
from datetime import datetime
from typing import Dict, Any, Optional
import tldextract
import dns.resolver
from whois import NICClient, WhoisEntry


def _query_whois_server(server: str, query: str, timeout: float = 3.0) -> str:
    """Executa uma consulta WHOIS garantindo o fechamento do socket em falhas."""
    response = bytearray()
    with socket.create_connection((server, 43), timeout=timeout) as connection:
        connection.settimeout(timeout)
        connection.sendall(query.encode("utf-8") + b"\r\n")
        while True:
            chunk = connection.recv(4096)
            if not chunk:
                break
            response.extend(chunk)
    return response.decode("utf-8", errors="replace")


def _load_whois_entry(domain: str):
    """Descobre o servidor no IANA e usa o parser do python-whois."""
    tld = domain.rsplit(".", 1)[-1]
    iana_response = _query_whois_server("whois.iana.org", tld)
    match = re.search(r"^whois:\s*(\S+)", iana_response, flags=re.IGNORECASE | re.MULTILINE)
    if not match:
        return None

    registry_server = match.group(1).strip()
    client = NICClient()
    raw_response = client.whois(
        domain,
        registry_server,
        NICClient.WHOIS_QUICK,
        quiet=True,
        ignore_socket_errors=False,
        timeout=3,
    )
    return WhoisEntry.load(domain, raw_response)

def get_domain_age_days(domain: str) -> Optional[int]:
    """
    Consulta o WHOIS do domínio para calcular sua idade em dias.
    Sites de golpe geralmente são registrados muito recentemente (< 30 dias).
    """
    try:
        w = _load_whois_entry(domain)
        if not w:
            return None
        creation_date = w.creation_date
        
        if isinstance(creation_date, list):
            creation_date = creation_date[0]
            
        if creation_date and isinstance(creation_date, datetime):
            now = datetime.now()
            age_days = (now - creation_date).days
            return max(age_days, 0)
    except Exception:
        pass
    return None

def check_ssl_certificate(domain: str) -> Dict[str, Any]:
    """
    Verifica se o domínio possui certificado SSL/TLS ativo e válido.
    """
    ssl_info = {
        "has_ssl": False,
        "issuer": "Desconhecido",
        "valid_until": None,
        "is_expired": True,
        "details": "Não foi possível verificar o certificado."
    }
    
    try:
        context = ssl.create_default_context()
        context.timeout = 3.0
        
        with socket.create_connection((domain, 443), timeout=3.0) as sock:
            with context.wrap_socket(sock, server_hostname=domain) as ssock:
                cert = ssock.getpeercert()
                if cert:
                    ssl_info["has_ssl"] = True
                    
                    # Emissor do certificado
                    issuer = dict(x[0] for x in cert.get('issuer', []))
                    ssl_info["issuer"] = issuer.get('organizationName', issuer.get('commonName', 'Emissor Conhecido'))
                    
                    # Data de expiração
                    not_after = cert.get('notAfter')
                    if not_after:
                        expire_date = datetime.strptime(not_after, '%b %d %H:%M:%S %Y %Z')
                        ssl_info["valid_until"] = expire_date.strftime('%Y-%m-%d')
                        ssl_info["is_expired"] = expire_date < datetime.now()
                        
                    ssl_info["details"] = "Certificado SSL/TLS ativo e válido."
    except Exception as e:
        ssl_info["details"] = f"Falha na verificação SSL: {str(e)}"
        
    return ssl_info

def check_dns_records(domain: str) -> Dict[str, Any]:
    """
    Consulta os registros DNS (A, MX) para validar se o domínio é operacional.
    """
    dns_status = {
        "has_a_record": False,
        "has_mx_record": False,
        "ip_addresses": []
    }
    
    try:
        # Checa registros A (Endereço IP)
        answers_a = dns.resolver.resolve(domain, 'A')
        dns_status["ip_addresses"] = [r.to_text() for r in answers_a]
        dns_status["has_a_record"] = len(dns_status["ip_addresses"]) > 0
    except Exception:
        pass

    try:
        # Checa registros MX (Servidor de E-mail)
        answers_mx = dns.resolver.resolve(domain, 'MX')
        dns_status["has_mx_record"] = len(answers_mx) > 0
    except Exception:
        pass

    return dns_status

def analyze_network(domain: str) -> Dict[str, Any]:
    """
    Executa a verificação completa de rede, SSL e WHOIS.
    """
    extracted = tldextract.extract(domain)
    domain_full = f"{extracted.domain}.{extracted.suffix}" if extracted.suffix else extracted.domain
    
    age_days = get_domain_age_days(domain_full)
    ssl_data = check_ssl_certificate(domain_full)
    dns_data = check_dns_records(domain_full)
    
    return {
        "domain_age_days": age_days,
        "is_recent_domain": age_days is not None and age_days < 30,
        "ssl": ssl_data,
        "dns": dns_data
    }
