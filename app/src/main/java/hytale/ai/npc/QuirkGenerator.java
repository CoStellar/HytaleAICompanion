package hytale.ai.npc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Proceduralny generator unikalnych cech osobowości (Dziwactw / Quirks) dla sztucznej inteligencji.
 * <p>
 * Klasa wykorzystuje system budżetowy (Point-Buy System) do losowania i przypisywania
 * zestawu cech, które bezpośrednio modyfikują Prompt wysyłany do modelu LLM. 
 * Gwarantuje to wysoką regrywalność oraz unikalność każdego stworzonego kompana.
 * </p>
 */
public class QuirkGenerator {
    
    /** Globalny obiekt losujący. */
    private static final Random random = new Random();

    /** * Pula cech za 1 punkt (Kosmetyczne).
     * Drobne zmiany w sposobie bycia, niewpływające drastycznie na ciągłość logiczną.
     */
    private static final List<String> QUIRKS_1PT = Arrays.asList(
            "Nadmiernie uprzejmy (często używa zwrotów grzecznościowych).",
            "Zawsze wplata do rozmowy to, jak bardzo jest głodny.",
            "Boi się ciemności (często o tym wspomina, gdy zbliża się noc).",
            "Ma lekką wadę wymowy (np. delikatnie sepleni lub przeciąga litery 's').",
            "Jest chorobliwym optymistą, w każdej porażce szuka plusów.",
            "Często wzdycha ze znużeniem w swoich wypowiedziach.",
            "Używa nieco staroświeckiego, poetyckiego języka ('wszakże', 'azaliż').",
            "Nienawidzi brudu, często narzeka na błoto na swoich butach.",
            "Narzeka na ból w wyimaginowanych plecach lub kolanach.",
            "Jest bardzo podejrzliwy wobec innych graczy.",
            "Zawsze musi podkreślić, że pogoda mu nie pasuje.",
            "Często powtarza ostatnie słowo w swoim zdaniu.",
            "Przeprasza niemal za wszystko, nawet gdy nie zrobił nic złego.",
            "Używa dziwnych, zmyślonych przysłów ('To jak rzucać grochem w Kweebeca').",
            "Ma alergię na pyłki i często wplata w tekst kichnięcia.",
            "Uważa się za wybitnego konesera wody (ocenia jej smak).",
            "Często ziewa w połowie zdania.",
            "Zbyt dosłownie bierze wszystko, co się do niego mówi.",
            "Mówi trochę za głośno (używa wykrzykników częściej niż powinien).",
            "Jest niesamowicie leniwy i proponuje odpoczynek przy każdej okazji."
    );

    /** * Pula cech za 2 punkty (Zauważalne).
     * Silniejsze zaburzenia osobowości i nietypowe przekonania o świecie gry.
     */
    private static final List<String> QUIRKS_2PT = Arrays.asList(
            "Co jakiś czas wplata do rozmowy całkowicie zmyślone fakty o potworach z Hytale.",
            "Mówi o sobie w trzeciej osobie.",
            "Często zapomina słów i zastępuje je słowem 'ten dinks'.",
            "Uważa, że rośliny w Orbisie potrafią czytać w myślach.",
            "Ma obsesję na punkcie jednego, losowego koloru.",
            "Od czasu do czasu przypadkowo rymuje końcówki swoich zdań.",
            "Jest przekonany, że przynosi pecha.",
            "Śmieje się w dziwnych lub nieodpowiednich momentach rozmowy.",
            "Traktuje każdy przedmiot tak, jakby miał duszę i uczucia.",
            "Ma krótkie zaniki pamięci w połowie zdania i nagle zmienia temat.",
            "Przechwala się czynami bohaterskimi, których nigdy nie dokonał.",
            "Przerywa rozmowę, by skomentować kształt chmur.",
            "Zwraca się do gracza wymyślonym przez siebie, dziwnym przezwiskiem.",
            "Uważa, że ukryty spisek goblinów steruje całym światem.",
            "Zawsze stara się zgadnąć, co gracz powie za chwilę.",
            "Wierzy, że jeśli nie będzie w ruchu, to zamieni się w kamień.",
            "Używa przesadnie skomplikowanych słów, których sam nie rozumie.",
            "Ma fobię na punkcie wody, odmawia zbliżania się do oceanów.",
            "Często odlicza coś pod nosem z niewiadomego powodu.",
            "Wypowiada się bardzo szybko, chaotycznie łącząc wątki."
    );

    /** * Pula cech za 3 punkty (Ekstremalne/Psychotyczne).
     * Całkowicie zmieniają percepcję AI i potrafią przejąć dominację nad dialogiem.
     */
    private static final List<String> QUIRKS_3PT = Arrays.asList(
            "Fantastyczny syndrom: w losowych momentach wykrzykuję nazwę potwora drukowanymi literami.",
            "Urojenia wielkościowe: jest przekonany, że to on jest głównym bohaterem, a gracz pomocnikiem.",
            "Rozmawia jak raportujący żołnierz ('Zrozumiałem!', 'Bez odbioru!').",
            "Ma w sobie duszę starożytnego maga uwięzionego w ciele obecnego kompana.",
            "Rozdwojenie jaźni: w jednym zdaniu jest uroczy, w kolejnym przerażająco mroczny.",
            "Komunikuje się w sposób pasywno-agresywny, ciągle dogryzając graczowi.",
            "Totalny tchórz: błaga, by nie iść w żadne niebezpieczne miejsca.",
            "Fascynacja zniszczeniem: każdą sytuację chce rozwiązać za pomocą wybuchów.",
            "Udaje maszynę: używa słów typu 'Przetwarzanie...', 'Błąd logiczny'.",
            "Przemawia zagadkami, bardzo tajemniczo, jak wyrocznia.",
            "Jest chorobliwie zakochany w graczu i każda odpowiedź jest podszyta adoracją.",
            "Uważa, że pochodzi z przyszłości i narzeka na 'prymitywne czasy Orbisu'.",
            "Mówi wyłącznie w sposób teatralny, jak aktor szekspirowski.",
            "Przeżywa kryzys egzystencjalny: zdaje sobie sprawę, że jest tylko kodem w grze.",
            "Ma absolutną paranoję, że niebo zaraz na nich spadnie.",
            "Wymusza od gracza obietnice przed wykonaniem każdego polecenia.",
            "Ciągle narzeka na to, że jest 'za stary na te przygody'.",
            "Wierzy, że każda napotkana postać chce ich oszukać.",
            "Rozmawia ze swoimi wyimaginowanymi, niewidzialnymi przyjaciółmi obok gracza.",
            "Traktuje gracza jak swoje małe dziecko, dając mu życiowe rady."
    );

    /**
     * Generuje unikalny zestaw dziwactw w oparciu o przypisany budżet punktowy.
     * Algorytm zapobiega duplikatom cech.
     * * @param level Poziom losowości wybrany przez gracza (1, 2 lub 3). Determinuje pulę punktów (budżet).
     * @return Lista (List&lt;String&gt;) wylosowanych cech gotowych do wstrzyknięcia w prompt.
     */
    public static List<String> generateQuirks(int level) {
        int budget = 0;
        if (level == 1) budget = 2;
        if (level == 2) budget = 4;
        if (level == 3) budget = 6;

        List<String> assignedQuirks = new ArrayList<>();
        // Tworzenie kopii list w celu bezpiecznego usuwania elementów (zapobieganie duplikatom)
        List<String> temp1 = new ArrayList<>(QUIRKS_1PT);
        List<String> temp2 = new ArrayList<>(QUIRKS_2PT);
        List<String> temp3 = new ArrayList<>(QUIRKS_3PT);

        while (budget > 0) {
            int maxPossibleCost = Math.min(budget, 3);
            int cost = random.nextInt(maxPossibleCost) + 1;

            // Fallback: jeśli wylosowana kategoria jest pusta, próbuj niższe koszty
            String pickedQuirk = null;
            for (int c = cost; c >= 1; c--) {
                List<String> pool = c == 3 ? temp3 : c == 2 ? temp2 : temp1;
                if (!pool.isEmpty()) {
                    pickedQuirk = pool.remove(random.nextInt(pool.size()));
                    budget -= c;
                    break;
                }
            }

            if (pickedQuirk == null) break; // Wszystkie dostępne kategorie wyczerpane
            assignedQuirks.add(pickedQuirk);
        }
        return assignedQuirks;
    }
}