# Hytale AI Companion

## Opis Projektu
Projekt realizowany w ramach przedmiotu Sztuczna Inteligencja w Grach Komputerowych.
Celem projektu jest implementacja wirtualnego agenta (NPC) w środowisku gry Hytale, wykorzystującego zewnętrzne modele języka (LLM) do dynamicznego generowania odpowiedzi oraz analizy stanu świata gry. System integruje się bezpośrednio z architekturą Entity Component System (ECS) silnika Hytale.

## Architektura Systemu

Modyfikacja opiera się na architekturze modularnej, separującej logikę komunikacji z API od zarządzania stanem gry:

* **Zarządzanie Zdarzeniami (Command Pattern):** Eliminacja scentralizowanego parsowania czatu. Wejście użytkownika jest przechwytywane przez `CommandManager` i delegowane do odpowiednich instancji implementujących interfejs `AICommand` w czasie $O(1)$ z wykorzystaniem mapowania (HashMap).
* **World Context Builder (Skanowanie Środowiska):** Moduł odpowiedzialny za odpytywanie silnika ECS. Kompiluje dane o stanie gracza (EntityStatMap, Inventory) oraz otoczeniu (TimeModule). Wykorzystuje optymalizowane obliczenia dystansu (kwadrat odległości Euklidesowej) do identyfikacji bytów w określonym promieniu od agenta, z pominięciem własnej referencji w celu uniknięcia sprzężenia zwrotnego w prompcie.
* **NpcBrain (Moduł Decyzyjny):** Odpowiada za zarządzanie oknem kontekstowym (Context Window) modelu. Wykorzystuje strukturę kolejki FIFO (LinkedList) do limitowania historii konwersacji (zabezpieczenie przed przekroczeniem limitu tokenów). Kontekst ustrukturyzowany jest poprzez Prompt Engineering z uwzględnieniem wstrzykiwania zachowań behawioralnych (Stance, Quirks).
* **Asynchroniczność:** Komunikacja z API zewnętrznym (Google Gemini) realizowana jest z wykorzystaniem `CompletableFuture`, co zapobiega blokowaniu głównego wątku serwera (Server Thread) i spadkom TPS (Ticks Per Second) podczas oczekiwania na odpowiedź HTTP.

## Zależności i Kompilacja (Środowisko Lokalne)

Ze względu na restrykcje licencyjne, repozytorium nie zawiera zastrzeżonych plików binarnych silnika serwerowego. Aby skompilować i uruchomić projekt w środowisku lokalnym, należy uzupełnić zależności:

1. Klonowanie repozytorium:
   `git clone <adres_repozytorium>`
2. W głównym drzewie projektu należy utworzyć katalog na lokalne biblioteki:
   `mkdir -p app/libs/`
3. Do nowo utworzonego katalogu należy skopiować oficjalny plik binarny serwera: `HytaleServer.jar`.
4. Projekt korzysta z narzędzia Gradle. Należy odświeżyć zależności w środowisku IDE (np. IntelliJ IDEA), aby zintegrować bibliotekę z procesem budowania.

## Struktura Komend Systemowych

Zarządzanie konfiguracją i stanem agenta realizowane jest przez interfejs CLI w oknie czatu gry. Argumenty parsowane są z przedrostkiem `!ai`:

* `-setup <API_KEY>` : Zapisuje klucz autoryzacyjny do lokalnego portfela ustawień gracza.
* `-create <Name> <Model> [Randomness]` : Alokuje pamięć na nowy profil NPC i wywołuje generator cech.
* `-summon` : Inicjuje renderowanie i podpinanie referencji ECS modelu w świecie gry.
* `-stance <PASYWNY|DEFENSYWNY|AGRESYWNY>` : Modyfikuje wektor zachowania agenta w kontekście wykrytych zagrożeń.
* `-debug` : Przełącza tryb deweloperski, zrzucając pełny skompilowany ciąg znaków (prompt) do logów serwera (standardowe wyjście).
* `-info` : Zwraca aktualny stan pamięci podręcznej przypisanej do UUID gracza.
* `-models` : Odpytuje rejestr Assetów serwera w celu wylistowania dostępnych modeli (implementuje paginację).
* `-clearmap` : Zwalnia zasoby w ECS, usuwając encje NPC nieprzypisane do żadnego aktywnego gracza.