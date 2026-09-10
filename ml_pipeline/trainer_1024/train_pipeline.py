import os
import random
import shutil
import time
from ultralytics import YOLO

# ==================== KONFIGURACJA ====================
IMG_SIZE = 1024         # Docelowa rozdzielczość obrazów
EPOCHS = 50             # Liczba epok treningowych
BATCH_SIZE = 12         # Zwiększony batch dla 1024x1024 (12 GB VRAM)
VAL_SPLIT = 0.2         # 20% danych na walidację
# ======================================================

def main():
    start_time = time.time()  # Start pomiaru czasu
    
    base_dir = os.getcwd()
    print(f"Przeszukiwanie folderu bazowego: {base_dir}")

    # Znajdź wszystkie foldery pasujące do wzorca dataset_v2_*
    dataset_folders = [
        os.path.join(base_dir, d) for d in os.listdir(base_dir)
        if d.startswith("dataset_v2_") and os.path.isdir(os.path.join(base_dir, d))
    ]

    if not dataset_folders:
        print("Błąd: Nie znaleziono żadnych folderów pasujących do wzorca dataset_v2_*.")
        return

    print(f"Znaleziono datasety do przetworzenia: {[os.path.basename(f) for f in dataset_folders]}")

    for ds_path in sorted(dataset_folders):
        ds_name = os.path.basename(ds_path)
        print(f"\n==========================================")
        print(f"Przetwarzanie datasetu: {ds_name}")
        print(f"==========================================\n")

        train_img_dir = os.path.join(ds_path, "train", "images")
        train_lbl_dir = os.path.join(ds_path, "train", "labels")
        val_img_dir = os.path.join(ds_path, "val", "images")
        val_lbl_dir = os.path.join(ds_path, "val", "labels")

        # Bezpieczne sprawdzenie, czy struktura train/val istnieje i NIE jest pusta
        already_split = (
            os.path.exists(train_img_dir) and 
            os.path.exists(val_img_dir) and 
            len(os.listdir(train_img_dir)) > 0
        )

        if not already_split:
            print("Dzielenie danych na train / val...")
            os.makedirs(train_img_dir, exist_ok=True)
            os.makedirs(train_lbl_dir, exist_ok=True)
            os.makedirs(val_img_dir, exist_ok=True)
            os.makedirs(val_lbl_dir, exist_ok=True)

            # Sprawdź, czy pliki są w podfolderach 'images' / 'labels', czy bezpośrednio w ds_path
            sub_img_dir = os.path.join(ds_path, "images")
            sub_lbl_dir = os.path.join(ds_path, "labels")

            if os.path.exists(sub_img_dir) and os.path.exists(sub_lbl_dir):
                source_img_dir = sub_img_dir
                source_lbl_dir = sub_lbl_dir
            else:
                source_img_dir = ds_path
                source_lbl_dir = ds_path

            # Pobierz wszystkie pliki PNG ze źródłowego folderu
            png_files = [
                f for f in os.listdir(source_img_dir)
                if f.lower().endswith(".png") and os.path.isfile(os.path.join(source_img_dir, f))
            ]

            # Dopasuj pliki PNG do odpowiadających im etykiet TXT
            valid_pairs = []
            for png_file in png_files:
                base_name = os.path.splitext(png_file)[0]
                txt_file = f"{base_name}.txt"
                txt_path = os.path.join(source_lbl_dir, txt_file)
                png_path = os.path.join(source_img_dir, png_file)
                if os.path.exists(txt_path):
                    valid_pairs.append((png_path, txt_path))

            if not valid_pairs:
                print(f"Ostrzeżenie: Brak par PNG/TXT w folderze {ds_name}. Pomijam.")
                continue

            # Losowy podział z zachowaniem powtarzalności (seed)
            random.seed(42)
            random.shuffle(valid_pairs)
            split_idx = int(len(valid_pairs) * (1 - VAL_SPLIT))
            train_pairs = valid_pairs[:split_idx]
            val_pairs = valid_pairs[split_idx:]

            print(f"Znaleziono {len(valid_pairs)} par. Przenoszenie -> Train: {len(train_pairs)}, Val: {len(val_pairs)}")

            # Przenoszenie plików do struktur YOLO
            for png_p, txt_p in train_pairs:
                shutil.move(png_p, os.path.join(train_img_dir, os.path.basename(png_p)))
                shutil.move(txt_p, os.path.join(train_lbl_dir, os.path.basename(txt_p)))

            for png_p, txt_p in val_pairs:
                shutil.move(png_p, os.path.join(val_img_dir, os.path.basename(png_p)))
                shutil.move(txt_p, os.path.join(val_lbl_dir, os.path.basename(txt_p)))
            
            # Usunięcie pustych folderów tymczasowych jeśli istniały
            if source_img_dir != ds_path:
                try:
                    os.rmdir(sub_img_dir)
                    os.rmdir(sub_lbl_dir)
                except OSError:
                    pass

            print("Podział zakończony pomyślnie.")
        else:
            print("Struktura train/val już istnieje w tym folderze i zawiera pliki. Pomijam krok podziału.")

        # Automatyczne utworzenie pliku konfiguracyjnego data.yaml dla 11 klas punktowych
        yaml_path = os.path.join(ds_path, "data.yaml")
        yaml_content = f"""path: {ds_path.replace(os.sep, '/')}
train: train/images
val: val/images

# Liczba klas: 11 (od 0 do 10 punktów)
nc: 11

# Nazwy klas przypisane do odpowiednich ID
names:
  0: '0 punktow'
  1: '1 punkt'
  2: '2 punkty'
  3: '3 punkty'
  4: '4 punkty'
  5: '5 punktow'
  6: '6 punktow'
  7: '7 punktow'
  8: '8 punktow'
  9: '9 punktow'
  10: '10 punktow'
"""
        with open(yaml_path, "w", encoding="utf-8") as f:
            f.write(yaml_content)
        print(f"Wygenerowano plik konfiguracyjny z 11 klasami: {yaml_path}")

        # Uruchomienie lokalnego treningu na GPU (RTX 4070 SUPER)
        print(f"Rozpoczynam trening modelu dla {ds_name} (Rozdzielczość: {IMG_SIZE}x{IMG_SIZE}, Epoki: {EPOCHS})...")
        
        model = YOLO("yolo11n.pt")

        model.train(
            data=yaml_path,
            epochs=EPOCHS,
            imgsz=IMG_SIZE,
            batch=BATCH_SIZE,
            device=0,                # Użycie pierwszej karty graficznej (RTX 4070 SUPER)
            workers=4,               # Liczba wątków ładowania danych
            project="target_detector_runs",
            name=f"run_{ds_name}"
        )
        print(f"Ukończono trening dla datasetu: {ds_name}!\n")

    print("Wszystkie zaplanowane treningi zostały pomyślnie zakończone!")

    # Podsumowanie czasu wykonania
    end_time = time.time()
    elapsed_seconds = end_time - start_time
    hours = int(elapsed_seconds // 3600)
    minutes = int((elapsed_seconds % 3600) // 60)
    seconds = int(elapsed_seconds % 60)
    print(f"Całkowity czas wykonania skryptu: {hours}h {minutes}m {seconds}s")

if __name__ == '__main__':
    main()