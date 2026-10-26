# UniMessenger

Prosty komunikator uczelniany: grupy, czat z prowadzącym, historia wiadomości na serwerze oraz pliki i zdjęcia do 1 GB.

To wersja demonstracyjna pod projekt studencki.

Projekt jest po angielsku (kod, interfejs, komunikaty API). 

## Części

- `app` — aplikacja Android, architektura MVVM
- `server` — mikroserwis REST (Ktor)
- `domain` — wspólne reguły: hasło, pliki, role

W wymaganiach jest zapis „MVVC”. Tu jest MVVM: ekran (View), ViewModel i repozytorium, które woła API.

## Uruchomienie serwera

Potrzebny Docker i JDK 17+.

```bash
docker compose up -d
./gradlew :server:run
```

API: `http://localhost:8080`. Sprawdzenie: `GET /health`.

PostgreSQL: baza `unimessenger`, użytkownik `postgres`, hasło `postgres`. Tabele tworzą się przy starcie. Relacja `students` → `student_items` powstaje, gdy student wysyła plik.

Konto prowadzącego, tworzone przy pierwszym starcie, jeśli jeszcze go nie ma:

- email: `teacher@uni.local`
- hasło: `Teacher12345`

Student zakłada konto sam: email i własne hasło (minimum 8 znaków, litera i cyfra).

## Aplikacja Android

Otwórz folder projektu w Android Studio i uruchom moduł `app`.

Adres serwera na ekranie logowania:

- emulator: `http://10.0.2.2:8080`
- telefon w tej samej sieci: `http://IP-komputera:8080`

Ekrany: logowanie, rejestracja studenta, lista kanałów, czat z załącznikiem.

## Pliki

Dozwolone są zdjęcia i zwykłe dokumenty. Zablokowane są m.in. `exe`, `dmg`, `apk`, `sh`, także gdy niebezpieczne rozszerzenie jest na końcu nazwy (`photo.jpg.exe`). Limit: 1 GB.

## Usuwanie

`DELETE /api/messages/{id}?mode=soft` ukrywa wiadomość. `mode=hard` usuwa ją z bazy razem z plikiem.

## Testy

```bash
./gradlew :domain:test :server:test
```

## Zostawione na później

- panel administratora i tworzenie kolejnych prowadzących
- odświeżanie czatu na żywo (teraz historia jest czytana z serwera przy wejściu w kanał)
- zapraszanie osób do grupy
