# 📚 Manager API Documentation

## Documentazione completa degli endpoint Manager con tutti gli input richiesti

**Base URL:** `http://localhost:8080/api/manager`

**Autenticazione:** Tutte le API richiedono il token JWT nell'header `Authorization`

---

## 🔐 Header Richiesto per Tutte le Chiamate

```
Authorization: Bearer <JWT_TOKEN>
```

Il token si ottiene effettuando il login come Manager tramite:
```
POST /api/auth/login
{
    "email": "manager@example.com",
    "password": "your_password"
}
```

---

## 🏠 PROPERTY MANAGEMENT

### 1. GET /api/manager/properties
**Descrizione:** Recupera tutte le proprietà del manager

**Input:**
| Parametro | Tipo | Posizione | Obbligatorio | Descrizione |
|-----------|------|-----------|--------------|-------------|
| Authorization | String | Header | ✅ | Token JWT (Bearer token) |

**Esempio cURL:**
```bash
curl -X GET "http://localhost:8080/api/manager/properties" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIs..."
```

**Risposta Successo (200):**
```json
[
  {
    "id": "64abc123...",
    "name": "Villa Bella Vista",
    "description": "Beautiful villa with sea view",
    "city": "Roma",
    "region": "Lazio",
    "country": "Italy",
    "pricePerNight": 150.0,
    "rating": 4.5,
    "amenities": ["WiFi", "Pool", "AC"],
    "photos": ["url1", "url2"],
    "coordinates": [12.4964, 41.9028]
  }
]
```

---

### 2. POST /api/manager/properties
**Descrizione:** Aggiunge una nuova proprietà

**Input:**
| Parametro | Tipo | Posizione | Obbligatorio | Descrizione |
|-----------|------|-----------|--------------|-------------|
| Authorization | String | Header | ✅ | Token JWT (Bearer token) |
| name | String | Body | ✅ | Nome della proprietà |
| city | String | Body | ✅ | Città |
| country | String | Body | ✅ | Paese |
| address | String | Body | ❌ | Indirizzo completo |
| description | String | Body | ❌ | Descrizione della proprietà |
| region | String | Body | ❌ | Regione |
| email | String | Body | ❌ | Email di contatto |
| amenities | Array[String] | Body | ❌ | Lista servizi (es: ["WiFi", "Pool"]) |
| photos | Array[String] | Body | ❌ | Lista URL foto |
| coordinates | Array[Double] | Body | ❌ | [latitudine, longitudine] |

**Esempio cURL:**
```bash
curl -X POST "http://localhost:8080/api/manager/properties" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIs..." \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Villa Bella Vista",
    "address": "Via Roma 123",
    "description": "Beautiful villa with sea view and pool",
    "city": "Roma",
    "region": "Lazio",
    "country": "Italy",
    "email": "villa@example.com",
    "amenities": ["WiFi", "Pool", "AC", "Parking"],
    "photos": ["https://example.com/photo1.jpg", "https://example.com/photo2.jpg"],
    "coordinates": [41.9028, 12.4964]
  }'
```

**Risposta Successo (201):**
```json
{
  "id": "64abc123def456...",
  "name": "Villa Bella Vista",
  "city": "Roma",
  ...
}
```

**Errori Possibili:**
| Codice | Messaggio | Causa |
|--------|-----------|-------|
| 400 | "Property name is required" | Nome mancante |
| 400 | "City is required" | Città mancante |
| 400 | "Country is required" | Paese mancante |
| 403 | "Only managers can perform this action" | Utente non è un Manager |

---

### 3. PUT /api/manager/properties/{propertyId}
**Descrizione:** Modifica una proprietà esistente

**Input:**
| Parametro | Tipo | Posizione | Obbligatorio | Descrizione |
|-----------|------|-----------|--------------|-------------|
| Authorization | String | Header | ✅ | Token JWT (Bearer token) |
| propertyId | String | URL Path | ✅ | ID della proprietà da modificare |
| name | String | Body | ❌ | Nuovo nome |
| city | String | Body | ❌ | Nuova città |
| country | String | Body | ❌ | Nuovo paese |
| ... | ... | Body | ❌ | (tutti i campi di PropertyRequestDTO) |

**Esempio cURL:**
```bash
curl -X PUT "http://localhost:8080/api/manager/properties/64abc123def456" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIs..." \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Villa Bella Vista - Updated",
    "description": "Updated description with more details"
  }'
```

**Errori Possibili:**
| Codice | Messaggio | Causa |
|--------|-----------|-------|
| 403 | "You can only modify your own properties" | Non sei il proprietario |
| 404 | "Property not found" | ID proprietà non valido |

---

### 4. DELETE /api/manager/properties/{propertyId}
**Descrizione:** Elimina una proprietà

**Input:**
| Parametro | Tipo | Posizione | Obbligatorio | Descrizione |
|-----------|------|-----------|--------------|-------------|
| Authorization | String | Header | ✅ | Token JWT (Bearer token) |
| propertyId | String | URL Path | ✅ | ID della proprietà da eliminare |

**Esempio cURL:**
```bash
curl -X DELETE "http://localhost:8080/api/manager/properties/64abc123def456" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIs..."
```

**Errori Possibili:**
| Codice | Messaggio | Causa |
|--------|-----------|-------|
| 403 | "You can only delete your own properties" | Non sei il proprietario |
| 404 | "Property not found" | ID non valido |
| 409 | "Cannot delete property with X active reservations" | Prenotazioni attive presenti |

---

## 🛏️ ROOM MANAGEMENT

### 5. GET /api/manager/properties/{propertyId}/rooms
**Descrizione:** Recupera tutte le stanze di una proprietà

**Input:**
| Parametro | Tipo | Posizione | Obbligatorio | Descrizione |
|-----------|------|-----------|--------------|-------------|
| Authorization | String | Header | ✅ | Token JWT |
| propertyId | String | URL Path | ✅ | ID della proprietà |

**Esempio cURL:**
```bash
curl -X GET "http://localhost:8080/api/manager/properties/64abc123/rooms" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIs..."
```

---

### 6. POST /api/manager/properties/{propertyId}/rooms
**Descrizione:** Aggiunge una stanza a una proprietà

**Input:**
| Parametro | Tipo | Posizione | Obbligatorio | Descrizione |
|-----------|------|-----------|--------------|-------------|
| Authorization | String | Header | ✅ | Token JWT |
| propertyId | String | URL Path | ✅ | ID della proprietà |
| name | String | Body | ✅ | Nome della stanza (es: "Camera Blu") |
| roomType | String | Body | ✅ | Tipo stanza: "Single", "Double", "Suite", "Family" |
| numBeds | Short | Body | ✅ | Numero di letti (deve essere > 0) |
| capacityAdults | Long | Body | ✅ | Capienza adulti (deve essere > 0) |
| capacityChildren | Long | Body | ✅ | Capienza bambini (può essere 0) |
| pricePerNightAdults | Float | Body | ✅ | Prezzo per notte adulti (deve essere > 0) |
| pricePerNightChildren | Float | Body | ✅ | Prezzo per notte bambini (può essere 0) |
| amenities | Array[String] | Body | ❌ | Servizi stanza (es: ["TV", "MiniBar"]) |
| photos | Array[String] | Body | ❌ | URL foto stanza |
| status | String | Body | ❌ | Stato: "available" (default) o "maintenance" |

**Esempio cURL:**
```bash
curl -X POST "http://localhost:8080/api/manager/properties/64abc123/rooms" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIs..." \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Camera Blu con Vista Mare",
    "roomType": "Double",
    "numBeds": 2,
    "capacityAdults": 2,
    "capacityChildren": 1,
    "pricePerNightAdults": 85.50,
    "pricePerNightChildren": 25.00,
    "amenities": ["TV", "MiniBar", "Balcony", "Sea View"],
    "photos": ["https://example.com/room1.jpg"],
    "status": "available"
  }'
```

**Risposta Successo (201):**
```json
{
  "id": "uuid-generated-id",
  "propertyId": "64abc123",
  "name": "Camera Blu con Vista Mare",
  "roomType": "Double",
  "numBeds": 2,
  "status": "available",
  "capacityAdults": 2,
  "capacityChildren": 1,
  "pricePerNightAdults": 85.50,
  "pricePerNightChildren": 25.00,
  "amenities": ["TV", "MiniBar", "Balcony", "Sea View"],
  "photos": ["https://example.com/room1.jpg"]
}
```

**Errori Possibili:**
| Codice | Messaggio | Causa |
|--------|-----------|-------|
| 400 | "Room name is required" | Nome stanza mancante |
| 400 | "Room type is required" | Tipo stanza mancante |
| 400 | "Adult capacity must be positive" | Capienza adulti <= 0 |
| 400 | "Price per night must be positive" | Prezzo <= 0 |
| 403 | "You can only add rooms to your own properties" | Non proprietario |
| 404 | "Property not found" | ID proprietà non valido |

---

### 7. PUT /api/manager/properties/{propertyId}/rooms/{roomId}
**Descrizione:** Modifica una stanza

**Input:**
| Parametro | Tipo | Posizione | Obbligatorio | Descrizione |
|-----------|------|-----------|--------------|-------------|
| Authorization | String | Header | ✅ | Token JWT |
| propertyId | String | URL Path | ✅ | ID proprietà |
| roomId | String | URL Path | ✅ | ID stanza |
| (campi RoomRequestDTO) | ... | Body | ❌ | Solo i campi da modificare |

**Esempio cURL:**
```bash
curl -X PUT "http://localhost:8080/api/manager/properties/64abc123/rooms/room456" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIs..." \
  -H "Content-Type: application/json" \
  -d '{
    "pricePerNightAdults": 95.00,
    "status": "maintenance"
  }'
```

---

### 8. DELETE /api/manager/properties/{propertyId}/rooms/{roomId}
**Descrizione:** Elimina una stanza

**Input:**
| Parametro | Tipo | Posizione | Obbligatorio | Descrizione |
|-----------|------|-----------|--------------|-------------|
| Authorization | String | Header | ✅ | Token JWT |
| propertyId | String | URL Path | ✅ | ID proprietà |
| roomId | String | URL Path | ✅ | ID stanza da eliminare |

**Esempio cURL:**
```bash
curl -X DELETE "http://localhost:8080/api/manager/properties/64abc123/rooms/room456" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIs..."
```

**Errori Possibili:**
| Codice | Messaggio | Causa |
|--------|-----------|-------|
| 409 | "Cannot delete room with X active reservations" | Prenotazioni attive |

---

## 📊 ANALYTICS

### 9. GET /api/manager/analytics
**Descrizione:** Analytics unificato - se `propertyId` è fornito restituisce analytics per quella proprietà, altrimenti per tutte le proprietà del manager

**Input:**
| Parametro | Tipo | Posizione | Obbligatorio | Descrizione |
|-----------|------|-----------|--------------|-------------|
| Authorization | String | Header | ✅ | Token JWT |
| propertyId | String | Query Param | ❌ | ID proprietà (se assente: tutte le proprietà) |
| startDate | String | Query Param | ❌ | Data inizio (formato: YYYY-MM-DD) |
| endDate | String | Query Param | ❌ | Data fine (formato: YYYY-MM-DD) |

**Esempio cURL:**
```bash
# Analytics per TUTTE le proprietà del manager
curl -X GET "http://localhost:8080/api/manager/analytics" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIs..."

# Analytics per una singola proprietà
curl -X GET "http://localhost:8080/api/manager/analytics?propertyId=64abc123" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIs..."

# Analytics per periodo specifico (tutte le proprietà)
curl -X GET "http://localhost:8080/api/manager/analytics?startDate=2025-01-01&endDate=2025-12-31" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIs..."

# Analytics per una proprietà in un periodo
curl -X GET "http://localhost:8080/api/manager/analytics?propertyId=64abc123&startDate=2025-01-01&endDate=2025-12-31" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIs..."
```

**Risposta Successo (200):**
```json
{
  "propertyId": "64abc123",
  "propertyName": "Villa Bella Vista",
  "periodStart": "2025-01-01",
  "periodEnd": "2025-12-31",
  "totalReservations": 45,
  "confirmedReservations": 38,
  "cancelledReservations": 5,
  "completedReservations": 30,
  "totalRevenue": 12500.00,
  "averageRevenuePerReservation": 328.95,
  "occupancyRate": 72.5,
  "totalNightsBooked": 265,
  "totalGuests": 95,
  "totalAdults": 78,
  "totalChildren": 17,
  "mostBookedRoomType": "Double Room",
  "avgGuestsPerRoomPerBooking": 2.3,
  "roomAnalytics": [
    {
      "roomId": "room1",
      "roomName": "Camera Blu",
      "reservationCount": 20,
      "revenue": 5600.00,
      "occupancyRate": 75.0
    }
  ],
  "monthlyBreakdown": {
    "2025-01": {"reservations": 5, "revenue": 1200.00, "guests": 12},
    "2025-02": {"reservations": 4, "revenue": 980.00, "guests": 8}
  }
}
```

---

### 10. GET /api/manager/analytics/all
**Descrizione:** Analytics per tutte le proprietà del manager

**Input:**
| Parametro | Tipo | Posizione | Obbligatorio | Descrizione |
|-----------|------|-----------|--------------|-------------|
| Authorization | String | Header | ✅ | Token JWT |
| startDate | String | Query Param | ❌ | Data inizio (YYYY-MM-DD) |
| endDate | String | Query Param | ❌ | Data fine (YYYY-MM-DD) |

**Esempio cURL:**
```bash
curl -X GET "http://localhost:8080/api/manager/analytics/all?startDate=2025-01-01&endDate=2025-06-30" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIs..."
```

---

### 11. GET /api/manager/analytics/summary
**Descrizione:** Riepilogo aggregato di tutte le proprietà

**Input:** Identico a `/analytics/all`

**Esempio cURL:**
```bash
curl -X GET "http://localhost:8080/api/manager/analytics/summary" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIs..."
```

**Campi Analytics aggiuntivi:**
| Campo | Descrizione |
|-------|-------------|
| `mostBookedRoomType` | Tipologia di camera più prenotata (es. "Double Room", "Suite") |
| `avgGuestsPerRoomPerBooking` | Media ospiti per camera per prenotazione |
| `occupancyRate` | Tasso di occupazione percentuale (0-100) |
| `totalRevenue` | Ricavi totali nel periodo |
| `totalReservations` | Numero totale prenotazioni |
| `roomAnalytics` | Dettaglio per ogni camera |
| `monthlyBreakdown` | Breakdown mensile (prenotazioni, ricavi, ospiti) |

---

## 📈 ADVANCED ANALYTICS (Business Intelligence)

### 12. GET /api/manager/analytics/ratings/{propertyId}
**Descrizione:** Analisi dell'evoluzione delle valutazioni nel tempo

**Input:**
| Parametro | Tipo | Posizione | Obbligatorio | Descrizione |
|-----------|------|-----------|--------------|-------------|
| Authorization | String | Header | ✅ | Token JWT |
| propertyId | String | URL Path | ✅ | ID proprietà |
| startDate | String | Query Param | ❌ | Data inizio (YYYY-MM-DD) |
| endDate | String | Query Param | ❌ | Data fine (YYYY-MM-DD) |

**Esempio cURL:**
```bash
curl -X GET "http://localhost:8080/api/manager/analytics/ratings/64abc123?startDate=2025-01-01&endDate=2025-12-31" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIs..."
```

**Risposta Successo (200):**
```json
{
  "currentAverageRating": 4.35,
  "previousPeriodRating": 4.10,
  "ratingTrend": 6.1,
  "totalReviews": 45,
  "monthlyAverageRatings": {
    "2025-01": 4.2,
    "2025-02": 4.3,
    "2025-03": 4.5
  },
  "avgCleanliness": 4.5,
  "avgCommunication": 4.3,
  "avgLocation": 4.8,
  "avgValue": 4.0,
  "ratingDistribution": {
    "1": 2,
    "2": 3,
    "3": 5,
    "4": 15,
    "5": 20
  },
  "bestAspect": "Location",
  "worstAspect": "Value"
}
```

**Campi risposta:**
| Campo | Descrizione |
|-------|-------------|
| `currentAverageRating` | Media valutazioni nel periodo selezionato |
| `previousPeriodRating` | Media del periodo precedente (per confronto) |
| `ratingTrend` | % di miglioramento/peggioramento vs periodo precedente |
| `monthlyAverageRatings` | Media mensile per tracciare l'evoluzione |
| `ratingDistribution` | Distribuzione delle valutazioni (1-5 stelle) |
| `bestAspect` / `worstAspect` | Aspetto migliore/peggiore della proprietà |

---

### 13. GET /api/manager/analytics/trends/{propertyId}
**Descrizione:** Analisi dei pattern di prenotazione e tendenze

**Input:**
| Parametro | Tipo | Posizione | Obbligatorio | Descrizione |
|-----------|------|-----------|--------------|-------------|
| Authorization | String | Header | ✅ | Token JWT |
| propertyId | String | URL Path | ✅ | ID proprietà |
| startDate | String | Query Param | ❌ | Data inizio (YYYY-MM-DD) |
| endDate | String | Query Param | ❌ | Data fine (YYYY-MM-DD) |

**Esempio cURL:**
```bash
curl -X GET "http://localhost:8080/api/manager/analytics/trends/64abc123?startDate=2025-01-01&endDate=2025-12-31" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIs..."
```

**Risposta Successo (200):**
```json
{
  "avgBookingsPerMonth": 8.5,
  "bookingGrowthRate": 15.3,
  "bookingsByDayOfWeek": {
    "MONDAY": 12,
    "TUESDAY": 15,
    "WEDNESDAY": 18,
    "THURSDAY": 20,
    "FRIDAY": 35,
    "SATURDAY": 40,
    "SUNDAY": 25
  },
  "bookingsByMonth": {
    "2025-01": 6,
    "2025-02": 8,
    "2025-03": 12
  },
  "avgLeadTimeDays": 14.5,
  "minLeadTimeDays": 1.0,
  "maxLeadTimeDays": 90.0,
  "avgStayDuration": 3.2,
  "stayDurationDistribution": {
    "1-2 nights": 25,
    "3-5 nights": 45,
    "6-7 nights": 20,
    "1-2 weeks": 8,
    "2+ weeks": 2
  },
  "cancellationRate": 12.5,
  "monthlyCancellationRates": {
    "2025-01": 15.0,
    "2025-02": 10.0,
    "2025-03": 8.0
  },
  "peakMonths": ["2025-07", "2025-08", "2025-12"],
  "lowSeasonMonths": ["2025-01", "2025-02", "2025-11"]
}
```

**Campi risposta:**
| Campo | Descrizione |
|-------|-------------|
| `avgBookingsPerMonth` | Media prenotazioni mensili |
| `bookingGrowthRate` | Tasso di crescita % (seconda metà vs prima metà periodo) |
| `bookingsByDayOfWeek` | Distribuzione check-in per giorno della settimana |
| `avgLeadTimeDays` | Giorni medi tra prenotazione e check-in |
| `avgStayDuration` | Durata media del soggiorno in notti |
| `stayDurationDistribution` | Distribuzione durata soggiorni |
| `cancellationRate` | Tasso di cancellazione % |
| `peakMonths` | Mesi di alta stagione (>25% sopra media) |
| `lowSeasonMonths` | Mesi di bassa stagione (<25% sotto media) |

---

### 14. GET /api/manager/analytics/benchmark/{propertyId}
**Descrizione:** Benchmarking comparativo con proprietà simili nella stessa area

**Input:**
| Parametro | Tipo | Posizione | Obbligatorio | Descrizione |
|-----------|------|-----------|--------------|-------------|
| Authorization | String | Header | ✅ | Token JWT |
| propertyId | String | URL Path | ✅ | ID proprietà |

**Esempio cURL:**
```bash
curl -X GET "http://localhost:8080/api/manager/analytics/benchmark/64abc123" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIs..."
```

**Risposta Successo (200):**
```json
{
  "comparisonScope": "Same City: Roma",
  "propertiesCompared": 45,
  "propertyRevenue": 52000.00,
  "avgMarketRevenue": 38500.00,
  "revenuePercentile": 78.5,
  "propertyOccupancy": 72.5,
  "avgMarketOccupancy": 65.0,
  "occupancyPercentile": 68.0,
  "propertyRating": 4.35,
  "avgMarketRating": 4.1,
  "ratingPercentile": 72.0,
  "propertyAvgPrice": 95.00,
  "marketAvgPrice": 85.00,
  "pricePositioning": "Mid-Range",
  "overallPerformanceScore": 73.15,
  "performanceCategory": "Above Average"
}
```

**Campi risposta:**
| Campo | Descrizione |
|-------|-------------|
| `comparisonScope` | Ambito del confronto (città/regione) |
| `propertiesCompared` | Numero di proprietà nel benchmark |
| `revenuePercentile` | Percentile ricavi (78.5 = meglio del 78.5% dei concorrenti) |
| `occupancyPercentile` | Percentile occupazione |
| `ratingPercentile` | Percentile valutazioni |
| `pricePositioning` | Posizionamento prezzo: "Budget", "Mid-Range", "Premium" |
| `overallPerformanceScore` | Score complessivo 0-100 (ponderato) |
| `performanceCategory` | "Top Performer", "Above Average", "Average", "Needs Improvement" |

---

## ⭐ REVIEWS BY PERIOD

### 15. GET /api/manager/reviews
**Descrizione:** Lista tutte le reviews di tutte le proprietà del manager, con filtro opzionale per periodo

**Input:**
| Parametro | Tipo | Posizione | Obbligatorio | Descrizione |
|-----------|------|-----------|--------------|-------------|
| Authorization | String | Header | ✅ | Token JWT |
| startDate | String | Query Param | ❌ | Data inizio (YYYY-MM-DD) |
| endDate | String | Query Param | ❌ | Data fine (YYYY-MM-DD) |

**Esempio cURL:**
```bash
# Tutte le reviews
curl -X GET "http://localhost:8080/api/manager/reviews" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIs..."

# Reviews in un periodo specifico
curl -X GET "http://localhost:8080/api/manager/reviews?startDate=2025-01-01&endDate=2025-06-30" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIs..."
```

**Risposta Successo (200):**
```json
[
  {
    "id": "rev123",
    "reservationId": "res456",
    "userId": "user789",
    "creationDate": "2025-03-15",
    "text": "Ottimo soggiorno, personale cordiale",
    "rating": 5,
    "cleanliness": 4.5,
    "communication": 5.0,
    "location": 4.0,
    "value": 4.5,
    "managerReply": "Grazie per il feedback!"
  }
]
```

---

### 16. GET /api/manager/reviews/property/{propertyId}
**Descrizione:** Lista le reviews di una specifica proprietà, con filtro opzionale per periodo

**Input:**
| Parametro | Tipo | Posizione | Obbligatorio | Descrizione |
|-----------|------|-----------|--------------|-------------|
| Authorization | String | Header | ✅ | Token JWT |
| propertyId | String | URL Path | ✅ | ID proprietà |
| startDate | String | Query Param | ❌ | Data inizio (YYYY-MM-DD) |
| endDate | String | Query Param | ❌ | Data fine (YYYY-MM-DD) |

**Esempio cURL:**
```bash
curl -X GET "http://localhost:8080/api/manager/reviews/property/64abc123?startDate=2025-01-01&endDate=2025-12-31" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIs..."
```

---

## 📅 RESERVATIONS VIEW

### 17. GET /api/manager/reservations
**Descrizione:** Tutte le prenotazioni delle proprietà del manager, con filtro opzionale per periodo

**Input:**
| Parametro | Tipo | Posizione | Obbligatorio | Descrizione |
|-----------|------|-----------|--------------|-------------|
| Authorization | String | Header | ✅ | Token JWT |
| startDate | String | Query Param | ❌ | Data inizio (checkIn >= startDate) |
| endDate | String | Query Param | ❌ | Data fine (checkOut <= endDate) |

**Esempio cURL:**
```bash
# Tutte le prenotazioni
curl -X GET "http://localhost:8080/api/manager/reservations" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIs..."

# Prenotazioni in un periodo specifico
curl -X GET "http://localhost:8080/api/manager/reservations?startDate=2025-01-01&endDate=2025-06-30" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIs..."
```

**Risposta Successo (200):**
```json
[
  {
    "reservationId": "res123",
    "status": "CONFIRMED",
    "checkIn": "2025-02-15",
    "checkOut": "2025-02-20",
    "createdAt": "2025-01-10T14:30:00",
    "adults": 2,
    "children": 1,
    "guestId": "user456",
    "guestName": "Mario Rossi",
    "guestEmail": "mario@example.com",
    "propertyId": "prop789",
    "propertyName": "Villa Bella Vista",
    "roomId": "room123",
    "roomName": "Camera Blu",
    "roomType": "Double",
    "totalPrice": 425.00
  }
]
```

---

### 18. GET /api/manager/reservations/property/{propertyId}
**Descrizione:** Prenotazioni per una singola proprietà, con filtro opzionale per periodo

**Input:**
| Parametro | Tipo | Posizione | Obbligatorio | Descrizione |
|-----------|------|-----------|--------------|-------------|
| Authorization | String | Header | ✅ | Token JWT |
| propertyId | String | URL Path | ✅ | ID proprietà |
| startDate | String | Query Param | ❌ | Data inizio (checkIn >= startDate) |
| endDate | String | Query Param | ❌ | Data fine (checkOut <= endDate) |

**Esempio cURL:**
```bash
curl -X GET "http://localhost:8080/api/manager/reservations/property/64abc123?startDate=2025-01-01&endDate=2025-12-31" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIs..."
```---

### 19. GET /api/manager/reservations/status/{status}
**Descrizione:** Filtra prenotazioni per stato

**Input:**
| Parametro | Tipo | Posizione | Obbligatorio | Descrizione |
|-----------|------|-----------|--------------|-------------|
| Authorization | String | Header | ✅ | Token JWT |
| status | String | URL Path | ✅ | Stato: `CONFIRMED`, `CANCELLED`, `COMPLETED`, `PENDING_PAYMENT` |

**Esempio cURL:**
```bash
curl -X GET "http://localhost:8080/api/manager/reservations/status/CONFIRMED" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIs..."
```

---

### 20. GET /api/manager/reservations/upcoming
**Descrizione:** Prenotazioni future (check-in dopo oggi)

**Input:**
| Parametro | Tipo | Posizione | Obbligatorio | Descrizione |
|-----------|------|-----------|--------------|-------------|
| Authorization | String | Header | ✅ | Token JWT |

---

### 21. GET /api/manager/reservations/current
**Descrizione:** Soggiorni attualmente in corso

**Input:**
| Parametro | Tipo | Posizione | Obbligatorio | Descrizione |
|-----------|------|-----------|--------------|-------------|
| Authorization | String | Header | ✅ | Token JWT |

---

## 💳 PAYMENT STATUS

### 22. GET /api/manager/payment-status
**Descrizione:** Stato pagamenti di tutte le stanze

**Input:**
| Parametro | Tipo | Posizione | Obbligatorio | Descrizione |
|-----------|------|-----------|--------------|-------------|
| Authorization | String | Header | ✅ | Token JWT |

**Esempio cURL:**
```bash
curl -X GET "http://localhost:8080/api/manager/payment-status" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIs..."
```

**Risposta Successo (200):**
```json
[
  {
    "propertyId": "prop123",
    "propertyName": "Villa Bella Vista",
    "rooms": [
      {
        "roomId": "room1",
        "roomName": "Camera Blu",
        "roomType": "Double",
        "availabilityStatus": "available",
        "currentlyOccupied": true,
        "currentReservationId": "res456",
        "currentGuestId": "user789",
        "currentCheckIn": "2025-01-20",
        "currentCheckOut": "2025-01-25",
        "paymentStatus": "CONFIRMED",
        "upcomingReservations": 3,
        "totalRevenueGenerated": 2500.00,
        "totalCompletedBookings": 15
      }
    ]
  }
]
```

---

### 23. GET /api/manager/payment-status/property/{propertyId}
**Descrizione:** Stato pagamenti per una singola proprietà

**Input:**
| Parametro | Tipo | Posizione | Obbligatorio | Descrizione |
|-----------|------|-----------|--------------|-------------|
| Authorization | String | Header | ✅ | Token JWT |
| propertyId | String | URL Path | ✅ | ID proprietà |

---

## ❌ Codici di Errore Comuni

| Codice HTTP | Significato | Descrizione |
|-------------|-------------|-------------|
| 400 | Bad Request | Input non valido o mancante |
| 401 | Unauthorized | Token JWT mancante o scaduto |
| 403 | Forbidden | Non sei un Manager o non sei il proprietario |
| 404 | Not Found | Risorsa non trovata (property/room) |
| 409 | Conflict | Impossibile completare (es: prenotazioni attive) |
| 500 | Internal Server Error | Errore del server |

---

## 📝 Note Importanti

1. **Date Format:** Tutte le date devono essere in formato ISO: `YYYY-MM-DD` (es: `2025-01-15`)

2. **Token JWT:** Il token scade dopo 1 ora. Effettuare nuovo login se scaduto.

3. **Solo Manager:** Tutti questi endpoint sono accessibili SOLO agli utenti con ruolo `MANAGER`

4. **Proprietà:** Un Manager può operare SOLO sulle proprie proprietà

5. **Eliminazione:** Non è possibile eliminare proprietà/stanze con prenotazioni attive (stato CONFIRMED con checkout futuro)
