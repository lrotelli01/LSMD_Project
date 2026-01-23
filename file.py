# Per usare questo script, devi avere la libreria fpdf installata.
# Se usi Google Colab, esegui prima: !pip install fpdf

from fpdf import FPDF

class PDF(FPDF):
    def header(self):
        self.set_font('Arial', 'B', 15)
        self.cell(0, 10, 'Nuovi Endpoint API', 0, 1, 'C')
        self.ln(5)

    def footer(self):
        self.set_y(-15)
        self.set_font('Arial', 'I', 8)
        self.cell(0, 10, f'Pagina {self.page_no()}', 0, 0, 'C')

def create_pdf():
    pdf = PDF()
    pdf.add_page()
    pdf.set_auto_page_break(auto=True, margin=15)

    # Dati degli endpoint
    data = {
        "Property Management": [
            ("GET", "/api/manager/properties", "Lista proprieta del manager"),
            ("POST", "/api/manager/properties", "Aggiungi proprieta"),
            ("PUT", "/api/manager/properties/{id}", "Modifica proprieta"),
            ("DELETE", "/api/manager/properties/{id}", "Elimina proprieta")
        ],
        "Room Management": [
            ("GET", "/api/manager/properties/{id}/rooms", "Lista stanze"),
            ("POST", "/api/manager/properties/{id}/rooms", "Aggiungi stanza"),
            ("PUT", "/api/manager/properties/{id}/rooms/{roomId}", "Modifica stanza"),
            ("DELETE", "/api/manager/properties/{id}/rooms/{roomId}", "Elimina stanza")
        ],
        "Analytics": [
            ("GET", "/api/manager/analytics/property/{id}...", "Analytics per proprieta"),
            ("GET", "/api/manager/analytics/all...", "Analytics tutte le proprieta"),
            ("GET", "/api/manager/analytics/summary...", "Analytics aggregate")
        ],
        "Reservations View": [
            ("GET", "/api/manager/reservations", "Tutte le prenotazioni"),
            ("GET", "/api/manager/reservations/property/{id}", "Prenotazioni per proprieta"),
            ("GET", "/api/manager/reservations/status/{status}", "Filtra per stato"),
            ("GET", "/api/manager/reservations/upcoming", "Prenotazioni future"),
            ("GET", "/api/manager/reservations/current", "Soggiorni in corso")
        ],
        "Payment Status": [
            ("GET", "/api/manager/payment-status", "Stato pagamenti tutte le stanze"),
            ("GET", "/api/manager/payment-status/property/{id}", "Stato pagamenti per proprieta")
        ]
    }

    # Impostazioni tabella
    col_widths = [25, 95, 70]
    line_height = 8

    for section, endpoints in data.items():
        # Titolo Sezione
        pdf.set_font('Arial', 'B', 12)
        pdf.set_fill_color(200, 220, 255)
        pdf.cell(0, 10, section, 0, 1, 'L', fill=True)
        
        # Header Tabella
        pdf.set_font('Arial', 'B', 10)
        pdf.set_fill_color(240, 240, 240)
        header = ["Metodo", "Endpoint", "Descrizione"]
        for i, h in enumerate(header):
            pdf.cell(col_widths[i], line_height, h, 1, 0, 'C', fill=True)
        pdf.ln()

        # Righe Tabella
        pdf.set_font('Arial', '', 9)
        for row in endpoints:
            pdf.cell(col_widths[0], line_height, row[0], 1)
            pdf.cell(col_widths[1], line_height, row[1], 1)
            pdf.cell(col_widths[2], line_height, row[2], 1)
            pdf.ln()
        
        pdf.ln(5) # Spazio tra sezioni

    # Footer finale
    pdf.ln(10)
   
    pdf.output("Nuovi_Endpoint_API.pdf")
    print("PDF creato con successo: Nuovi_Endpoint_API.pdf")

if __name__ == "__main__":
    create_pdf()