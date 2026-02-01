import requests
import random
import string
import json
from datetime import datetime, timedelta
 
# ================= CONFIGURAZIONE =================
BASE_URL = "http://localhost:8080/api"
HEADERS = {"Content-Type": "application/json"}
 
# Colori per output
GREEN = '\033[92m'
RED = '\033[91m'
YELLOW = '\033[93m'
RESET = '\033[0m'
 
def print_step(message):
    print(f"\n{YELLOW}➡️  {message}{RESET}")
 
def print_pass(message):
    print(f"{GREEN}✅ [PASS] {message}{RESET}")
 
def print_fail(message, response=None):
    print(f"{RED}❌ [FAIL] {message}{RESET}")
    if response:
        print(f"   Status: {response.status_code}")
        try:
            print(f"   Body: {json.dumps(response.json(), indent=2)}")
        except:
            print(f"   Body: {response.text}")
 
# ================= GENERATORI DATI =================
def generate_user():
    suffix = ''.join(random.choices(string.ascii_lowercase + string.digits, k=5))
    return {
        "username": f"user_{suffix}",
        "email": f"test_{suffix}@example.com",
        "password": "Password1!", # Rispetta la regex complessa
        "phoneNumber": "1234567890",
        "birthdate": "1990-01-01",
        "role": "CUSTOMER"
    }
 
# ================= MAIN TEST =================
def run_tests():
    print(f"{YELLOW}🚀 INIZIO TEST AUTOMATICO LARGEB&B{RESET}")
 
    # 1. REGISTRAZIONE
    print_step("1. Registrazione Nuovo Utente")
    user_data = generate_user()
    res = requests.post(f"{BASE_URL}/auth/register", json=user_data, headers=HEADERS)
    if res.status_code == 200:
        print_pass(f"Utente creato: {user_data['email']}")
    else:
        print_fail("Registrazione fallita", res)
        return
 
    # 2. LOGIN
    print_step("2. Login e Acquisizione Token")
    login_payload = {"email": user_data['email'], "password": user_data['password']}
    res = requests.post(f"{BASE_URL}/auth/login", json=login_payload, headers=HEADERS)
    token = None
    if res.status_code == 200:
        token = res.json().get("token")
        print_pass("Login effettuato, Token ricevuto")
    else:
        print_fail("Login fallito", res)
        return
 
    AUTH_HEADERS = HEADERS.copy()
    AUTH_HEADERS["Authorization"] = f"Bearer {token}"
 
    # 3. RICERCA PROPRIETÀ
    print_step("3. Ricerca Proprietà a 'Rome'")
    # Nota: Assicurati di aver popolato il DB con città = 'Rome'
    res = requests.get(f"{BASE_URL}/properties/search?city=Rome", headers=HEADERS)
    target_property_id = None
    target_room_id = None
    if res.status_code == 200:
        props = res.json()
        if len(props) > 0:
            target_property_id = props[0]['id']
            print_pass(f"Trovate {len(props)} case. Selezionata ID: {target_property_id}")
        else:
            print_fail("Nessuna casa trovata a Roma. (DB Vuoto o città errata?)")
            return
    else:
        print_fail("Errore API Search", res)
        return
 
    # 4. DETTAGLI PROPRIETÀ (Trigger Redis Trending + History)
    print_step("4. Visita Dettagli (Popola History & Trending)")
    # Recuperiamo i dettagli COMPLETI per trovare una stanza
    res = requests.get(f"{BASE_URL}/properties/{target_property_id}", headers=AUTH_HEADERS)
    if res.status_code == 200:
        data = res.json()
        print_pass("Dettagli recuperati")
        # Cerchiamo una stanza per dopo
        # Nota: La struttura del JSON dipende dal tuo DTO, qui cerco di indovinare o adattare
        # Se il DTO non ha le stanze esposte direttamente, questo step potrebbe fallire
        # Ma nel tuo PropertyResponseDTO non vedo la lista delle stanze... 
        # Controllo il codice: PropertyResponseDTO ha il prezzo minimo ma non la lista stanze.
        # DOVREBBE averla se Room è embedded. 
        # Se non c'è, useremo un endpoint diverso se esiste o tireremo a indovinare.
        # Guardando il tuo codice: Property ha List<Room>, ma PropertyResponseDTO NON ha List<RoomDTO>!
        # Ha solo amenities, foto, pois. 
        # ATTENZIONE: Se non esponi le stanze nel DTO, il frontend non può prenotare!
        # Verifico il file PropertyResponseDTO...
        # ... Ah, nel file PropertyResponseDTO.java che hai mandato NON c'è il campo 'rooms'.
        # Questo è un bug architetturale: come fa l'utente a scegliere la stanza?
        print(f"{YELLOW}⚠️  ATTENZIONE: PropertyResponseDTO non sembra esporre la lista delle stanze.{RESET}")
        print("    Tenterò di prenotare recuperando le stanze da un'altra chiamata se esiste,")
        print("    oppure saltiamo la prenotazione.")
    else:
        print_fail("Errore Dettagli", res)
 
    # 5. CONTROLLO STORICO (Redis)
    print_step("5. Verifica Storico Utente (Redis)")
    res = requests.get(f"{BASE_URL}/properties/history", headers=AUTH_HEADERS)
    if res.status_code == 200:
        history = res.json()
        # Controlliamo se l'ID della casa visitata è nella lista
        found = any(p['id'] == target_property_id for p in history)
        if found:
            print_pass("La casa visitata è apparsa nello storico!")
        else:
            print_fail("La casa NON è nello storico. (Hai aggiornato PropertyController?)")
    else:
        print_fail("Errore API History", res)
 
    # 6. MAPPA GEOSPAZIALE
    print_step("6. Test Mappa (Coordinate Roma)")
    # Coordinate Pantheon
    res = requests.get(f"{BASE_URL}/properties/map?lat=41.8986&lon=12.4768&radiusKm=5", headers=HEADERS)
    if res.status_code == 200:
        map_results = res.json()
        if len(map_results) > 0:
            print_pass(f"Mappa OK: Trovate {len(map_results)} case vicine.")
        else:
            print_fail("Mappa vuota. (Hai messo *1000 nel Service? Le coordinate nel DB sono corrette?)")
    else:
        print_fail("Errore API Map", res)
 
    # 7. RACCOMANDAZIONI (Neo4j)
    print_step("7. Test Raccomandazioni (Neo4j)")
    res = requests.get(f"{BASE_URL}/properties/{target_property_id}/recommendations/similar", headers=HEADERS)
    if res.status_code == 200:
        print_pass("Content-Based (Simili) OK")
    else:
        print_fail("Errore Content-Based", res)
 
    res = requests.get(f"{BASE_URL}/properties/{target_property_id}/recommendations/collaborative", headers=HEADERS)
    if res.status_code == 200:
        print_pass("Collaborative (Utenti simili) OK")
    else:
        print_fail("Errore Collaborative", res)
 
    # 8. NOTIFICHE
    print_step("8. Controllo Notifiche")
    res = requests.get(f"{BASE_URL}/notifications", headers=AUTH_HEADERS)
    if res.status_code == 200:
        print_pass("API Notifiche risponde correttamente")
    else:
        print_fail("Errore Notifiche", res)
    print(f"\n{YELLOW}🏁 TEST COMPLETATO.{RESET}")
 
if __name__ == "__main__":
    try:
        run_tests()
    except requests.exceptions.ConnectionError:
        print(f"{RED}ERRORE FATALE: Impossibile connettersi a localhost:8080.{RESET}")
        print("Assicurati che Spring Boot sia avviato!")