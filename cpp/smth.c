#include <stdio.h>
#include <stdlib.h>
#include <time.h>

int main() {
    srand(time(0));  
    int randnum = rand() % 100 + 1; 
    int input;

    do {
        printf("Enter:");
        scanf("%d", &input);
        if (input > randnum) {
            printf("lower \n");
        } else if (input < randnum) {
            printf("higher \n");
        } else {
            printf("gj \n");
        }
    } while (input != randnum);

    return 0;
}
