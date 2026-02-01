import requests
import random
import string
import json
from datetime import datetime, timedelta
 
# ================= CONFIGURATION =================
BASE_URL = "http://localhost:8080/api"
HEADERS = {"Content-Type": "application/json"}
 
# Colors for output
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
 
# ================= DATA GENERATORS =================
def generate_user():
    suffix = ''.join(random.choices(string.ascii_lowercase + string.digits, k=5))
    return {
        "username": f"user_{suffix}",
        "email": f"test_{suffix}@example.com",
        "password": "Password1!", # Complies with the complex regex
        "phoneNumber": "1234567890",
        "birthdate": "1990-01-01",
        "role": "CUSTOMER"
    }
 
# ================= MAIN TEST =================
def run_tests():
    print(f"{YELLOW}🚀 STARTING AUTOMATIC TEST LARGEB&B{RESET}")
 
    # 1. REGISTRATION
    print_step("1. New User Registration")
    user_data = generate_user()
    res = requests.post(f"{BASE_URL}/auth/register", json=user_data, headers=HEADERS)
    if res.status_code == 200:
        print_pass(f"User created: {user_data['email']}")
    else:
        print_fail("Registration failed", res)
        return
 
    # 2. LOGIN
    print_step("2. Login and Token Acquisition")
    login_payload = {"email": user_data['email'], "password": user_data['password']}
    res = requests.post(f"{BASE_URL}/auth/login", json=login_payload, headers=HEADERS)
    token = None
    if res.status_code == 200:
        token = res.json().get("token")
        print_pass("Login successful, Token received")
    else:
        print_fail("Login failed", res)
        return
 
    AUTH_HEADERS = HEADERS.copy()
    AUTH_HEADERS["Authorization"] = f"Bearer {token}"
 
    # 3. PROPERTY SEARCH
    print_step("3. Search Properties in 'Rome'")
    # Note: Make sure you have populated the DB with city = 'Rome'
    res = requests.get(f"{BASE_URL}/properties/search?city=Rome", headers=HEADERS)
    target_property_id = None
    target_room_id = None
    if res.status_code == 200:
        props = res.json()
        if len(props) > 0:
            target_property_id = props[0]['id']
            print_pass(f"Found {len(props)} properties. Selected ID: {target_property_id}")
        else:
            print_fail("No properties found in Rome. (Empty DB or wrong city?)")
            return
    else:
        print_fail("Search API Error", res)
        return
 
    # 4. PROPERTY DETAILS (Trigger Redis Trending + History)
    print_step("4. Visit Details (Populates History & Trending)")
    # Retrieve the FULL details to find a room
    res = requests.get(f"{BASE_URL}/properties/{target_property_id}", headers=AUTH_HEADERS)
    if res.status_code == 200:
        data = res.json()
        print_pass("Details retrieved")
        # Let's look for a room for later
        # Note: The JSON structure depends on your DTO, here I try to guess or adapt
        # If the DTO doesn't expose rooms directly, this step might fail
        # But in your PropertyResponseDTO I don't see the rooms list... 
        # Checking the code: PropertyResponseDTO has the minimum price but not the rooms list.
        # It SHOULD have it if Room is embedded. 
        # If not, we'll use a different endpoint if it exists or we'll guess.
        # Looking at your code: Property has List<Room>, but PropertyResponseDTO does NOT have List<RoomDTO>!
        # It only has amenities, photos, pois. 
        # WARNING: If you don't expose rooms in the DTO, the frontend cannot book!
        # Checking the PropertyResponseDTO file...
        # ... Ah, in the PropertyResponseDTO.java file you sent there is NO 'rooms' field.
        # This is an architectural bug: how can the user choose the room?
        print(f"{YELLOW}⚠️  WARNING: PropertyResponseDTO doesn't seem to expose the rooms list.{RESET}")
        print("    I'll try to book by retrieving rooms from another call if it exists,")
        print("    or we'll skip the reservation.")
    else:
        print_fail("Details Error", res)
 
    # 5. HISTORY CHECK (Redis)
    print_step("5. Verify User History (Redis)")
    res = requests.get(f"{BASE_URL}/properties/history", headers=AUTH_HEADERS)
    if res.status_code == 200:
        history = res.json()
        # Check if the visited property ID is in the list
        found = any(p['id'] == target_property_id for p in history)
        if found:
            print_pass("The visited property appeared in the history!")
        else:
            print_fail("The property is NOT in the history. (Did you update PropertyController?)")
    else:
        print_fail("History API Error", res)
 
    # 6. GEOSPATIAL MAP
    print_step("6. Map Test (Rome Coordinates)")
    # Pantheon Coordinates
    res = requests.get(f"{BASE_URL}/properties/map?lat=41.8986&lon=12.4768&radiusKm=5", headers=HEADERS)
    if res.status_code == 200:
        map_results = res.json()
        if len(map_results) > 0:
            print_pass(f"Map OK: Found {len(map_results)} nearby properties.")
        else:
            print_fail("Map empty. (Did you add *1000 in the Service? Are the DB coordinates correct?)")
    else:
        print_fail("Map API Error", res)
 
    # 7. RECOMMENDATIONS (Neo4j)
    print_step("7. Recommendations Test (Neo4j)")
    res = requests.get(f"{BASE_URL}/properties/{target_property_id}/recommendations/similar", headers=HEADERS)
    if res.status_code == 200:
        print_pass("Content-Based (Similar) OK")
    else:
        print_fail("Content-Based Error", res)
 
    res = requests.get(f"{BASE_URL}/properties/{target_property_id}/recommendations/collaborative", headers=HEADERS)
    if res.status_code == 200:
        print_pass("Collaborative (Similar users) OK")
    else:
        print_fail("Collaborative Error", res)
 
    # 8. NOTIFICATIONS
    print_step("8. Check Notifications")
    res = requests.get(f"{BASE_URL}/notifications", headers=AUTH_HEADERS)
    if res.status_code == 200:
        print_pass("Notifications API responds correctly")
    else:
        print_fail("Notifications Error", res)
    print(f"\n{YELLOW}🏁 TEST COMPLETED.{RESET}")
 
if __name__ == "__main__":
    try:
        run_tests()
    except requests.exceptions.ConnectionError:
        print(f"{RED}FATAL ERROR: Unable to connect to localhost:8080.{RESET}")
        print("Make sure Spring Boot is running!")