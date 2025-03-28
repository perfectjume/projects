#include <stdio.h>
#include <stdlib.h>
#include <time.h>

int main() {
    srand(time(0));  
    int min;
    int max;

    printf("Set min:");
    get_int(&min);
    printf("Set max:");
    get_int(&max);

    int randnum = rand() % (max - min) + min; 
    int input;
    int uniqueTries = 0;

    int triednumbers_size= 0;
    int triednumbers_capacity= 0;
    int *triednumbers = malloc(triednumbers_capacity * sizeof(int));

    do {
        printf("Enter: ");
        get_int(&input);
        
        if (triednumbers_size == triednumbers_capacity)
        {
            triednumbers_capacity+=5;
            triednumbers = realloc(triednumbers,triednumbers_capacity * sizeof(int));
        }

        int exists = 0;
        for (int i = 0; i < triednumbers_size; i++) {
            if (triednumbers[i] == input) {
                exists = 1;
                break;
            } 
        }

        if (!exists)
        {
            triednumbers[triednumbers_size] = input;
            triednumbers_size++;
            uniqueTries++;
        }
        
        if (input > randnum) {
            printf("lower \n");
        } else if (input < randnum) {
            printf("higher \n");
        } else {
            printf("game over \n");
            printf("Unique tries: %d\n", uniqueTries);
        }
    } while (input != randnum);

    free(triednumbers);
    return 0;
}

void get_int(int *var) {
    while (1) {
        if (scanf("%d", var) == 1) break; // Break if valid integer is entered

        while (getchar() != '\n'); // Clear the input buffer
        printf("Please enter a valid integer.\n");
    }
}