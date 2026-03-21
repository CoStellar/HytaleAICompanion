# Sztuczna inteligencja w grach komputerowych: Określenie tematu oraz harmonogram projektu

**Autorzy:** Maria Słomiany (268576), Viktoria Novogrodskaia (288497), Patryk Wawrzacz (263880)  
**Kurs:** W04TAI-SM0402G (Politechnika Wrocławska)

---

## 1. Cel projektu
Celem projektu jest **zaprojektowanie i implementacja wirtualnego agenta** (kompana NPC) w środowisku przestrzennym gry komputerowej **Hytale**. Projekt zakłada stworzenie hybrydowego systemu sztucznej inteligencji, łączącego duże modele językowe (LLM) z algorytmami uczenia ze wzmocnieniem (ang. *Reinforcement Learning*). Grupa projektowa będzie dążyła do stworzenia kompana, który będzie jak najlepiej zakorzeniony w całym świecie gry, dobrze dopasuje się do gracza, a także będzie mógł go wspierać zarówno wiedzą pochodzącą z materiałów dotyczących gry, jak i fizyczną pomocą w trakcie rozgrywki.

## 2. Przewidywane technologie i architektura
* **Środowisko i silnik:** Praca będzie odbywać się na zmodyfikowanym serwerze gry Hytale.
* **Język programowania:** Java (wraz z narzędziem Gradle do budowania środowiska).
* **LLM:** API Google Gemini (asynchroniczna komunikacja HTTP/JSON z wykorzystaniem inżynierii promptów), z możliwą integracją z innymi modelami w przyszłości.
* **Reinforcement Learning:** Planowana jest implementacja algorytmu *Q-Learning* (Tabular RL) sterującego zachowaniami taktycznymi i przetrwaniem agenta w grze.
* **Kontrola wersji:** Kod przechowywany w repozytorium GitHub z wykorzystaniem modelu pracy *Git Flow* (branching zadaniowy, pull requests).

## 3. Główne moduły systemu

### 3.1. Moduł percepcji środowiskowej
Zintegrowany z silnikiem gry skrypt (`WorldContextBuilder`) będzie odpowiedzialny za ciągłą ekstrakcję danych o stanie świata. Będzie on agregował informacje o zdrowiu agenta i gracza, zawartości ekwipunku, czasie oraz wykorzysta zoptymalizowane obliczenia dystansu (kwadrat odległości Euklidesowej) do identyfikacji bytów i interaktywnych bloków w promieniu obserwacji.

### 3.2. Moduł komunikacji i parsowania akcji
Architektura systemu będzie opierać się na inżynierii promptów i wykorzystaniu dynamicznej pamięci krótkotrwałej w formie kolejki FIFO, co chroni przed przekroczeniem limitu tokenów. Moduł będzie przetwarzał zwrotne odpowiedzi chmury LLM przy użyciu wyrażeń regularnych (Regex), by przechwytywać z nich ukryte polecenia (tzw. Action-Parsing, np. `[ACTION:HEAL]`). Umożliwi to modelowi językowemu bezpośrednie ingerowanie w fizyczny stan świata gry (np. leczenie postaci, nakładanie efektów). Ważne będzie takie przekazanie informacji modelowi, aby zminimalizować ilość wysyłanych tokenów wraz z maksymalizacją kontekstu dotyczącego świata gry, jak i samego gracza.

### 3.3. Moduł uczenia ze wzmocnieniem (RL)
W celu uniezależnienia reakcji bojowych od opóźnień sieciowych API zewnętrznego, agent będzie korzystał z własnego, lokalnego modułu sterowania opartego na równaniu Bellmana:

$$Q(s, a) \leftarrow (1 - \alpha) Q(s, a) + \alpha \left( R + \gamma \max_{a'} Q(s', a') \right)$$

* **Przestrzeń stanów (S):** Początkowo będzie to poziom zdrowia gracza i agenta, dystans oraz liczba przeciwników zmapowanych przez ECS Radar.
* **Przestrzeń akcji (A):** System będzie przewidywał akcje takie jak atak fizyczny, leczenie, ucieczka czy pozycjonowanie.
* **Nagrody (R):** Przygotujemy system wartości, który będzie karał agenta za odniesione obrażenia i nagradzał za utrzymanie gracza przy życiu lub eliminację zagrożenia.

## 4. Harmonogram i kamienie milowe
Poniższy harmonogram zakłada weryfikację prac i integrację modułów w dwutygodniowych iteracjach.

### Architektura bazowa i integracja z ECS (31 marca 2026 r.)
* Konfiguracja środowiska, powołanie publicznego repozytorium GitHub oraz wdrożenie bezpiecznego portfela konfiguracji (CLI wewnątrz gry: `!ai -setup`).
* Zbudowanie bezkolizyjnego systemu komend w oparciu o wzorzec projektowy *Command*.
* Implementacja modułu `WorldContextBuilder` kompresującego dane ze świata ECS (radar otoczenia, stan inwentarza, HP).
* Opracowanie struktury promptu bazowego ze wstrzykiwaniem osobowości oraz pamięci asynchronicznej konwersacji (kolejka FIFO).

### Sprawczość, reaktywność i Action-Parsing (14 kwietnia 2026 r.)
* Implementacja systemu Action-Parsing (przechwytywanie tagów z odpowiedzi modelu LLM).
* Podpięcie logiki silnika ECS pod polecenia AI (fizyczne leczenie postaci wyzwalane decyzją modelu).
* Stworzenie pętli nasłuchującej w grze, umożliwiającej spontaniczne inicjowanie dialogu przez NPC w odpowiedzi na zmiany środowiskowe.

### Podstawy walki i zarządzanie błędami (28 kwietnia 2026 r.)
* Zbudowanie mechaniki zadawania obrażeń potworom przez NPC oraz systemu śmierci/odrodzenia kompana.
* Implementacja mechanizmów typu *Retry* chroniących system przed błędami HTTP/503 (timeout) ze strony Google Gemini API.

### Agent uczący się (12 maja 2026 r.)
* Utworzenie środowiska algorytmu RL (zdefiniowanie formalnych nagród i stanów w Javie).
* Zastąpienie testowych, statycznych trybów zachowań algorytmem *Q-Learning*, pozwalającym agentowi na samodzielne dobieranie taktyki co tick (cykl) serwera.

### Finalizacja i ewaluacja (26 maja 2026 r.)
* Integracja z graficznym interfejsem użytkownika (GUI) zastępującym polecenia tekstowe `!ai`.
* Zgromadzenie metryk i danych treningowych agenta RL z serwera testowego (wykresy nauki).
* Wygenerowanie finalnej dokumentacji kodu (JavaDoc).

## 5. Podział obowiązków w zespole
Wszystkie funkcje, które zamierzamy zaimplementować, znajdować się będą na tablicy Kanban (w naszym przypadku Trello), z której każdy z członków zespołu będzie wybierał konkretne zadania do wykonania. Na jeden dwutygodniowy blok roboczy przewidujemy od osoby wprowadzenie średnio 2 funkcji do systemu.