# Instalacja projektu target_ml

## Moduł Machine Learning

### Wymagane zależności
* **[Python 3.14](https://www.python.org/downloads/release/python-3144/)**
* **[YOLOv11 – Ultralytics](https://docs.ultralytics.com/models/yolo11/#performance-metrics)**

### Instalacja i konfiguracja środowiska
1. **Zainstaluj Pythona:** Pobierz i zainstaluj język Python w wersji 3.14 z oficjalnej strony podanej wyżej.
2. **Przejdź do projektu:** Otwórz terminal i przejdź do głównego folderu projektu (np. `/Users/jmaciazek-mac/Documents/PI-UKSW/target-ml`).
3. **Utworzenie i aktywacja środowisko wirtualnego:** Wykonaj poniższe polecenie, aby stworzyć odizolowane środowisko o nazwie `venv`:

```bash
python -m venv venv # Utworzenie 
source venv/bin/activate # Aktywacja
pip install -r requirements.txt --log install.log # Instalacja wymaganych bibliotek
```
