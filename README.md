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

Na potrzeby lokalnego demo ustaw `ENABLE_DEMO_TEACHER=true`, aby włączyć konto demonstracyjne. Domyślnie serwer nie tworzy tego konta i odrzuca logowanie na wcześniej utworzone konto demonstracyjne.

- email: `teacher@uni.local`
- hasło: `Teacher12345`

Student zakłada konto sam: email i własne hasło (minimum 8 znaków, litera i cyfra).

## Rejestracja wykładowcy

Po sprawdzeniu statusu wykładowcy pracownik dziekanatu z dostępem do środowiska serwera wydaje kod dla jego adresu e-mail i jednostki. Polecenie korzysta z tych samych ustawień bazy co serwer. W środowisku uczelni ustaw własny `JWT_SECRET`.

```bash
./gradlew -q :server:run --args='issue-teacher-code anna@uni.local "Computer science"'
```

Przekaż wykładowcy wyświetlony kod prywatnym kanałem. Kod ma sześć cyfr (może zaczynać się od zera), jest ważny przez 24 godziny i blokuje się po pięciu błędnych próbach. Ponowne wydanie zastępuje poprzedni kod. Rejestracja wykorzystuje kod tylko raz i przypisuje jednostkę podaną przez dziekanat.

W aplikacji wybierz „Rejestruję się jako wykładowca”, przejdź do potwierdzenia i wpisz kod przypisany do adresu e-mail z formularza.

## Aplikacja Android

Otwórz folder projektu w Android Studio i uruchom moduł `app`.

Adres serwera na ekranie logowania:

- emulator: `http://10.0.2.2:8080`
- telefon w tej samej sieci: `http://IP-komputera:8080`

Ekrany obejmują logowanie, rejestrację studenta i wykładowcy, listę kanałów, profil i czat. Z listy kanałów otwórz „Mój profil”, aby zmienić nazwę wyświetlaną. Rola, e-mail i jednostka pozostają danymi konta.

## Rozmowy i grupy

Dotknij wiadomości, aby otworzyć menu odpowiedzi, reakcji i kopiowania. Własne wiadomości można też edytować, ukrywać lub usuwać trwale. Menu paska rozmowy umożliwia wyszukiwanie w treści wiadomości i nazwach załączników. Właściciel grupy może z tego menu dodać uczestnika po adresie e-mail istniejącego konta.

## Pliki

Dozwolone są zdjęcia i zwykłe dokumenty. Zablokowane są m.in. `exe`, `dmg`, `apk`, `sh`, także gdy niebezpieczne rozszerzenie jest na końcu nazwy (`photo.jpg.exe`). Limit: 1 GB.

## Na żywo

Otwarty czat trzyma WebSocket `/api/channels/{id}/live` z nagłówkiem `Authorization: Bearer`. Serwer wysyła zdarzenie, gdy wiadomość dodano, zmieniono albo usunięto. Zwykłe wysyłanie i historia nadal idą przez HTTP.

## Usuwanie

`DELETE /api/messages/{id}?mode=soft` ukrywa wiadomość. `mode=hard` usuwa ją z bazy razem z plikiem.

## Testy

```bash
./gradlew :domain:test :server:test :app:assembleDebug
./gradlew :domain:pitest
```

PIT zapisuje raport dla reguł rejestracji i plików w `domain/build/reports/pitest`. Test PostgreSQL uruchamia się po ustawieniu `TEST_DB_URL`, np. `jdbc:postgresql://localhost:5432/unimessenger`, oraz odpowiednich `TEST_DB_USER` i `TEST_DB_PASSWORD`. Użyj testowej bazy z prawem do tworzenia schematów. Test tworzy własny schemat i usuwa go po zakończeniu.

## Zostawione na później

- panel administratora
