public Person[] getPeople_unsafe$Modification(int startIndex, int maxCount) {
    Person[] results;
    int peopleCount = people.size();
    int start = startIndex;
    if (start >= peopleCount) {
        results = NO_PEOPLE;
    }
    int end = Math.min(startIndex + maxCount, peopleCount);
    if (start == end) {
        results = NO_PEOPLE;
    }
    int resultCount = end - start;
    results = new Person[resultCount];
    for (int from = start, to = 0; to < resultCount; ++from , ++to) {
        results[to] = people.get(from);
    }
    return results;
}