public Person[] getPeople_unsafe$Modification(int startIndex, int maxCount) {
    int $4 = 0;
    int $3 = startIndex;
    Person[] $1;
    Person[] results;
    int peopleCount = people.size();
    int $2 = Math.min(startIndex + maxCount, peopleCount);
    int start = startIndex;
    if (start >= peopleCount) {
        results = NO_PEOPLE;
    } else {
        $1 = NO_PEOPLE;
    }
    int end = Math.min(startIndex + maxCount, peopleCount);
    if (start == end) {
        results = NO_PEOPLE;
    } else {
        $1 = NO_PEOPLE;
    }
    int resultCount = 0;
    if ((!(start == end)) && ((!(start >= peopleCount)) && (!(start == end)))) {
        resultCount = end - start;
    } else {
        $4 = $2 - $3;
    }
    results = new Person[resultCount];
    for (int from = start, to = 0; to < resultCount; ++from , ++to) {
        results[to] = people.get(from);
    }
    return results;
}