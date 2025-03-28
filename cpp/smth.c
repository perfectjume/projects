#include <stdio.h>
#include <stdlib.h>
#include <math.h>
#include <time.h>

#define NUM_BODIES 1000
#define STEPS 1000
#define G 6.67430e-11

typedef struct {
    double x, y, vx, vy, mass;
} Body;

Body bodies[NUM_BODIES];

void initialize_bodies() {
    for (int i = 0; i < NUM_BODIES; i++) {
        bodies[i].x = rand() / (double)RAND_MAX * 1000;
        bodies[i].y = rand() / (double)RAND_MAX * 1000;
        bodies[i].vx = 0;
        bodies[i].vy = 0;
        bodies[i].mass = rand() / (double)RAND_MAX * 1000 + 1;
    }
}

void update_bodies() {
    for (int i = 0; i < NUM_BODIES; i++) {
        double fx = 0, fy = 0;
        for (int j = 0; j < NUM_BODIES; j++) {
            if (i == j) continue;
            double dx = bodies[j].x - bodies[i].x;
            double dy = bodies[j].y - bodies[i].y;
            double dist = sqrt(dx * dx + dy * dy);
            if (dist == 0) continue;
            double force = (G * bodies[i].mass * bodies[j].mass) / (dist * dist);
            fx += force * dx / dist;
            fy += force * dy / dist;
        }
        bodies[i].vx += fx / bodies[i].mass;
        bodies[i].vy += fy / bodies[i].mass;
    }

    for (int i = 0; i < NUM_BODIES; i++) {
        bodies[i].x += bodies[i].vx;
        bodies[i].y += bodies[i].vy;
    }
}

int main() {
    srand(time(0));  
    initialize_bodies();

    // Start timer
    clock_t start = clock();

    for (int step = 0; step < STEPS; step++) {
        update_bodies();
    }

    // End timer
    clock_t end = clock();
    double duration = (double)(end - start) / CLOCKS_PER_SEC;

    printf("Simulation complete in %.3f seconds.\n", duration);


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